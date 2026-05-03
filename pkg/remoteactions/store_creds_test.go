package remoteactions

import (
	"context"
	"testing"
	"time"

	"github.com/glebarez/sqlite"
	"gorm.io/gorm"
)

func newTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	if err := db.Exec("PRAGMA foreign_keys = ON").Error; err != nil {
		t.Fatalf("fk on: %v", err)
	}
	stmts := []string{
		`CREATE TABLE remote_otp_credentials (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			name TEXT NOT NULL UNIQUE,
			secret_b32 TEXT NOT NULL,
			algorithm TEXT NOT NULL DEFAULT 'sha1',
			digits INTEGER NOT NULL DEFAULT 6,
			period INTEGER NOT NULL DEFAULT 30,
			created_at DATETIME NOT NULL,
			last_used_at DATETIME
		)`,
		`CREATE TABLE remote_action_macros (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			target_call TEXT NOT NULL,
			label TEXT NOT NULL,
			action_name TEXT NOT NULL,
			args_string TEXT NOT NULL DEFAULT '',
			remote_otp_credential_id INTEGER,
			position INTEGER NOT NULL DEFAULT 0,
			created_at DATETIME NOT NULL,
			updated_at DATETIME NOT NULL,
			FOREIGN KEY (remote_otp_credential_id)
				REFERENCES remote_otp_credentials(id) ON DELETE SET NULL
		)`,
		`CREATE INDEX idx_remote_action_macros_target_call
			ON remote_action_macros(target_call)`,
	}
	for _, s := range stmts {
		if err := db.Exec(s).Error; err != nil {
			t.Fatalf("schema: %v", err)
		}
	}
	return db
}

func TestCredStoreCreateGetList(t *testing.T) {
	db := newTestDB(t)
	cs := NewCredStore(db)
	ctx := context.Background()

	c := &RemoteOTPCredential{Name: "NW5W OTP", SecretB32: "JBSWY3DPEHPK3PXP"}
	if err := cs.Create(ctx, c); err != nil {
		t.Fatalf("create: %v", err)
	}
	if c.ID == 0 || c.CreatedAt.IsZero() {
		t.Fatalf("Create did not stamp id/createdAt: %+v", c)
	}

	got, err := cs.Get(ctx, c.ID)
	if err != nil {
		t.Fatalf("get: %v", err)
	}
	if got.Name != "NW5W OTP" || got.SecretB32 != "JBSWY3DPEHPK3PXP" {
		t.Fatalf("get returned %+v", got)
	}

	list, err := cs.List(ctx)
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	if len(list) != 1 {
		t.Fatalf("list length = %d", len(list))
	}
}

func TestCredStoreUniqueName(t *testing.T) {
	db := newTestDB(t)
	cs := NewCredStore(db)
	ctx := context.Background()
	if err := cs.Create(ctx, &RemoteOTPCredential{Name: "dup", SecretB32: "JBSWY3DPEHPK3PXP"}); err != nil {
		t.Fatalf("first: %v", err)
	}
	err := cs.Create(ctx, &RemoteOTPCredential{Name: "dup", SecretB32: "JBSWY3DPEHPK3PXP"})
	if err == nil {
		t.Fatalf("expected unique constraint error")
	}
}

func TestCredStoreUpdateAndTouch(t *testing.T) {
	db := newTestDB(t)
	cs := NewCredStore(db)
	ctx := context.Background()
	c := &RemoteOTPCredential{Name: "NW5W OTP", SecretB32: "JBSWY3DPEHPK3PXP"}
	if err := cs.Create(ctx, c); err != nil {
		t.Fatalf("create: %v", err)
	}
	c.Name = "NW5W repeater"
	if err := cs.Update(ctx, c); err != nil {
		t.Fatalf("update: %v", err)
	}
	got, _ := cs.Get(ctx, c.ID)
	if got.Name != "NW5W repeater" {
		t.Fatalf("Update did not persist: %+v", got)
	}
	when := time.Now().UTC().Truncate(time.Second)
	if err := cs.TouchLastUsed(ctx, c.ID, when); err != nil {
		t.Fatalf("touch: %v", err)
	}
	got, _ = cs.Get(ctx, c.ID)
	if got.LastUsedAt == nil || !got.LastUsedAt.Equal(when) {
		t.Fatalf("TouchLastUsed did not stamp; got %v want %v", got.LastUsedAt, when)
	}
}

func TestCredStoreUsedBy(t *testing.T) {
	db := newTestDB(t)
	cs := NewCredStore(db)
	ms := NewMacroStore(db)
	ctx := context.Background()

	c := &RemoteOTPCredential{Name: "NW5W OTP", SecretB32: "JBSWY3DPEHPK3PXP"}
	if err := cs.Create(ctx, c); err != nil {
		t.Fatalf("create: %v", err)
	}
	for _, target := range []string{"KK7XYZ-9", "KK7XYZ-9", "W7ABC"} {
		m := &RemoteActionMacro{
			TargetCall:            target,
			Label:                 "x",
			ActionName:            "x",
			RemoteOTPCredentialID: &c.ID,
		}
		if err := ms.Create(ctx, m); err != nil {
			t.Fatalf("macro: %v", err)
		}
	}
	usedBy, err := cs.UsedBy(ctx)
	if err != nil {
		t.Fatalf("usedBy: %v", err)
	}
	got := usedBy[c.ID]
	if len(got) != 2 || got[0] == got[1] {
		t.Fatalf("expected 2 distinct targets, got %v", got)
	}
}

func TestCredStoreDelete(t *testing.T) {
	db := newTestDB(t)
	cs := NewCredStore(db)
	ctx := context.Background()
	c := &RemoteOTPCredential{Name: "NW5W OTP", SecretB32: "JBSWY3DPEHPK3PXP"}
	if err := cs.Create(ctx, c); err != nil {
		t.Fatalf("create: %v", err)
	}
	if err := cs.Delete(ctx, c.ID); err != nil {
		t.Fatalf("delete: %v", err)
	}
	if _, err := cs.Get(ctx, c.ID); err == nil {
		t.Fatalf("expected not-found after delete")
	}
}
