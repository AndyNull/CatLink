package version

import (
	"runtime"
	"strconv"

	"go-admin/cmd/migrate/migration"
	"go-admin/cmd/migrate/migration/models"
	common "go-admin/common/models"

	"gorm.io/gorm"
)

func init() {
	_, fileName, _, _ := runtime.Caller(0)
	migration.Migrate.SetVersion(migration.GetFilename(fileName), migrateCatLinkInformationArchitecture)
}

func migrateCatLinkInformationArchitecture(db *gorm.DB, version string) error {
	return db.Transaction(func(tx *gorm.DB) error {
		groups := []models.SysMenu{
			{MenuId: 10012, MenuName: "CatLinkRecords", Title: "用户与档案", Icon: "user", Path: "/catlink/records", Paths: "/0/10000/10012", MenuType: "M", ParentId: 10000, Component: "/catlink/index", Sort: 2, Visible: "0", IsFrame: "1"},
			{MenuId: 10013, MenuName: "CatLinkContent", Title: "内容与互动", Icon: "message", Path: "/catlink/content", Paths: "/0/10000/10013", MenuType: "M", ParentId: 10000, Component: "/catlink/index", Sort: 3, Visible: "0", IsFrame: "1"},
			{MenuId: 10014, MenuName: "CatLinkOperations", Title: "资产与治理", Icon: "component", Path: "/catlink/operations", Paths: "/0/10000/10014", MenuType: "M", ParentId: 10000, Component: "/catlink/index", Sort: 4, Visible: "0", IsFrame: "1"},
		}
		for i := range groups {
			if err := upsertCatLinkSeed(tx, &models.SysMenu{}, "menu_id = ?", groups[i].MenuId, &groups[i]); err != nil {
				return err
			}
		}
		if err := tx.Unscoped().Model(&models.SysMenu{}).Where("menu_id = ?", 10000).Updates(map[string]any{"title": "有猫腻运营", "sort": 1}).Error; err != nil {
			return err
		}
		parents := map[int][]int{
			10012: {10011, 10002, 10003},
			10013: {10004, 10008, 10009, 10010},
			10014: {10005, 10006, 10007},
		}
		for parentID, children := range parents {
			for sort, menuID := range children {
				paths := "/0/10000/" + strconv.Itoa(parentID) + "/" + strconv.Itoa(menuID)
				if err := tx.Unscoped().Model(&models.SysMenu{}).Where("menu_id = ?", menuID).Updates(map[string]any{"parent_id": parentID, "paths": paths, "sort": sort + 1}).Error; err != nil {
					return err
				}
			}
		}
		var role struct {
			RoleID int `gorm:"column:role_id"`
		}
		if err := tx.Table("ga_role").Select("role_id").Where("role_key = ? AND deleted_at = 0", "admin").Take(&role).Error; err != nil {
			return err
		}
		for _, group := range groups {
			if err := tx.Exec("INSERT INTO ga_role_menu (role_id, menu_id) SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM ga_role_menu WHERE role_id = ? AND menu_id = ?)", role.RoleID, group.MenuId, role.RoleID, group.MenuId).Error; err != nil {
				return err
			}
		}
		return tx.Create(&common.Migration{Version: version}).Error
	})
}
