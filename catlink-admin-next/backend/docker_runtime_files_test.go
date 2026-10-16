package main

import (
	"os"
	"strings"
	"testing"
)

func TestDockerImageIncludesMigrationSQL(t *testing.T) {
	source, err := os.ReadFile("Dockerfile")
	if err != nil {
		t.Fatal(err)
	}
	line := "COPY config/db-begin-mysql.sql config/db.sql config/db-end-mysql.sql /app/config/"
	if !strings.Contains(string(source), line) {
		t.Fatal("Docker image is missing MySQL migration seed files")
	}
	if !strings.Contains(string(source), "mkdir -p /app/static/uploadfile") {
		t.Fatal("Docker image does not prepare the persistent admin upload path")
	}
	for _, name := range []string{"config/db-begin-mysql.sql", "config/db.sql", "config/db-end-mysql.sql"} {
		if _, err := os.Stat(name); err != nil {
			t.Fatalf("missing runtime file %s: %v", name, err)
		}
	}
}
