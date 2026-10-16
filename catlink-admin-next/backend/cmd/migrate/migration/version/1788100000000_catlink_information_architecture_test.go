package version

import (
	"fmt"
	"testing"

	"github.com/glebarez/sqlite"
	"gorm.io/gorm"
)

func TestCatLinkInformationArchitectureMigratesExistingMenus(t *testing.T) {
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	for _, statement := range []string{
		`CREATE TABLE ga_menu (menu_id integer primary key, menu_name text, title text, icon text, path text, paths text, menu_type text, action text, permission text, parent_id integer, no_cache boolean, breadcrumb text, component text, sort integer, visible text, is_frame text, create_by integer, update_by integer, created_at datetime, updated_at datetime, deleted_at integer default 0)`,
		`CREATE TABLE ga_role (role_id integer primary key, role_key text, deleted_at integer default 0)`,
		`CREATE TABLE ga_role_menu (role_id integer, menu_id integer)`,
		`CREATE TABLE ga_migration (version text primary key, apply_time datetime)`,
		`INSERT INTO ga_role (role_id, role_key) VALUES (1, 'admin')`,
	} {
		if err := db.Exec(statement).Error; err != nil {
			t.Fatal(err)
		}
	}
	for _, menu := range catLinkOperationMenus() {
		if err := db.Table("ga_menu").Create(&menu).Error; err != nil {
			t.Fatal(err)
		}
	}

	for run := 1; run <= 2; run++ {
		if err := migrateCatLinkInformationArchitecture(db, fmt.Sprintf("test-%d", run)); err != nil {
			t.Fatalf("run %d: %v", run, err)
		}
	}

	var rows []struct {
		MenuID   int
		ParentID int
		Paths    string
	}
	if err := db.Raw("SELECT menu_id, parent_id, paths FROM ga_menu WHERE menu_id IN (10002,10004,10005,10012,10013,10014) ORDER BY menu_id").Scan(&rows).Error; err != nil {
		t.Fatal(err)
	}
	want := map[int]int{10002: 10012, 10004: 10013, 10005: 10014, 10012: 10000, 10013: 10000, 10014: 10000}
	for _, row := range rows {
		if row.ParentID != want[row.MenuID] {
			t.Errorf("menu %d parent = %d, want %d", row.MenuID, row.ParentID, want[row.MenuID])
		}
	}
	var bindings int64
	if err := db.Table("ga_role_menu").Where("role_id = ? AND menu_id IN ?", 1, []int{10012, 10013, 10014}).Count(&bindings).Error; err != nil {
		t.Fatal(err)
	}
	if bindings != 3 {
		t.Fatalf("group role bindings = %d, want 3", bindings)
	}
}
