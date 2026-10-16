package catlink

import (
	"fmt"
	"strings"
	"time"

	"github.com/flipped-aurora/gin-vue-admin/server/global"
	catmodel "github.com/flipped-aurora/gin-vue-admin/server/model/catlink"
	"gorm.io/gorm"
)

const (
	catStatusRescue    = "rescue"
	catStatusTreating  = "treating"
	catStatusAdoptable = "adoptable"
	catStatusAdopted   = "adopted"
	catStatusArchived  = "archived"

	archiveReasonLost     = "lost"
	archiveReasonDeceased = "deceased"
	archiveReasonOther    = "other"
)

var catStatusLabels = map[string]string{
	catStatusRescue: "救助中", catStatusTreating: "治疗中", catStatusAdoptable: "待领养",
	catStatusAdopted: "已领养", catStatusArchived: "已归档",
}

type catStatusError string

func (e catStatusError) Error() string { return string(e) }

func normalizeCatStatus(value string) (status, inferredArchiveReason string, ok bool) {
	switch strings.ToUpper(strings.TrimSpace(value)) {
	case "RESCUE", "RESCUING", "INTAKE", "救助中":
		return catStatusRescue, "", true
	case "TREATING", "RECOVERING", "恢复中", "治疗中":
		return catStatusTreating, "", true
	case "ADOPTABLE", "READY_FOR_ADOPTION", "MATCHING", "RETURNED", "待领养", "匹配中", "被退回":
		return catStatusAdoptable, "", true
	case "ADOPTED", "FOLLOW_UP", "已领养", "回访中":
		return catStatusAdopted, "", true
	case "ARCHIVED", "END", "已归档":
		return catStatusArchived, "", true
	case "DECEASED", "离世", "已离世":
		return catStatusArchived, archiveReasonDeceased, true
	case "LOST", "LOST_CONTACT", "失踪", "失联":
		return catStatusArchived, archiveReasonLost, true
	default:
		return "", "", false
	}
}

func normalizeArchiveReason(value string) (string, bool) {
	switch strings.ToUpper(strings.TrimSpace(value)) {
	case "LOST", "LOST_CONTACT", "失踪", "失联":
		return archiveReasonLost, true
	case "DECEASED", "离世", "已离世":
		return archiveReasonDeceased, true
	case "OTHER", "其他":
		return archiveReasonOther, true
	default:
		return "", false
	}
}

func validateCatStatusInput(rawStatus, rawArchiveReason string) (string, string, error) {
	if strings.TrimSpace(rawStatus) == "" {
		rawStatus = catStatusAdoptable
	}
	status, inferred, ok := normalizeCatStatus(rawStatus)
	if !ok {
		return "", "", fmt.Errorf("猫咪状态无效")
	}
	if status != catStatusArchived {
		return status, "", nil
	}
	reason := inferred
	if strings.TrimSpace(rawArchiveReason) != "" {
		var valid bool
		reason, valid = normalizeArchiveReason(rawArchiveReason)
		if !valid {
			return "", "", fmt.Errorf("归档原因无效")
		}
	}
	if reason == "" {
		return "", "", fmt.Errorf("归档必须选择失踪、离世或其他原因")
	}
	return status, reason, nil
}

func canChangeCatStatus(from, to, currentArchiveReason string) bool {
	if from == to {
		return true
	}
	if from == catStatusArchived && currentArchiveReason == archiveReasonDeceased {
		return false
	}
	allowed := map[string]map[string]bool{
		catStatusRescue:    {catStatusTreating: true, catStatusAdoptable: true, catStatusArchived: true},
		catStatusTreating:  {catStatusRescue: true, catStatusAdoptable: true, catStatusArchived: true},
		catStatusAdoptable: {catStatusRescue: true, catStatusTreating: true, catStatusAdopted: true, catStatusArchived: true},
		catStatusAdopted:   {catStatusAdoptable: true, catStatusArchived: true},
		catStatusArchived:  {catStatusRescue: true, catStatusTreating: true, catStatusAdoptable: true, catStatusAdopted: true},
	}
	return allowed[from][to]
}

func calendarEpochDay(value time.Time) int64 {
	year, month, day := value.Date()
	return time.Date(year, month, day, 0, 0, 0, 0, time.UTC).Unix() / 86400
}

func migrateCatStatuses() error {
	var rows []catmodel.CatPublicCat
	if err := global.GVA_DB.Select("id", "status", "cat_status", "archive_reason", "legacy_status").FindInBatches(&rows, 200, func(tx *gorm.DB, _ int) error {
		for _, row := range rows {
			status, inferred, known := normalizeCatStatus(row.Status)
			legacy := row.LegacyStatus
			if !known {
				legacy = row.Status
				status = catStatusRescue
			}
			reason, validReason := normalizeArchiveReason(row.ArchiveReason)
			if inferred != "" {
				reason = inferred
			} else if status == catStatusArchived && !validReason {
				reason = archiveReasonOther
			} else if status != catStatusArchived {
				reason = ""
			}
			if row.Status == status && row.CatStatus == catStatusLabels[status] && row.ArchiveReason == reason && row.LegacyStatus == legacy {
				continue
			}
			if err := tx.Model(&catmodel.CatPublicCat{}).Where("id = ?", row.ID).Updates(map[string]any{
				"status": status, "cat_status": catStatusLabels[status], "archive_reason": reason, "legacy_status": legacy,
			}).Error; err != nil {
				return err
			}
		}
		rows = rows[:0]
		return nil
	}).Error; err != nil {
		return err
	}
	if !global.GVA_DB.Migrator().HasTable(&catmodel.CatShare{}) {
		return nil
	}
	var shares []catmodel.CatShare
	return global.GVA_DB.Select("code", "status", "archive_reason").FindInBatches(&shares, 200, func(tx *gorm.DB, _ int) error {
		for _, row := range shares {
			status, inferred, known := normalizeCatStatus(row.Status)
			if !known {
				status = catStatusRescue
			}
			reason, validReason := normalizeArchiveReason(row.ArchiveReason)
			if inferred != "" {
				reason = inferred
			} else if status == catStatusArchived && !validReason {
				reason = archiveReasonOther
			} else if status != catStatusArchived {
				reason = ""
			}
			if row.Status == status && row.ArchiveReason == reason {
				continue
			}
			if err := tx.Model(&catmodel.CatShare{}).Where("code = ?", row.Code).Updates(map[string]any{
				"status": status, "archive_reason": reason,
			}).Error; err != nil {
				return err
			}
		}
		shares = shares[:0]
		return nil
	}).Error
}
