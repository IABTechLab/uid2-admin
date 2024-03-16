PRAGMA foreign_keys = 1;

CREATE TABLE "site" (
	"id"	INTEGER NOT NULL,
	"name"	TEXT NOT NULL,
	"description"	TEXT,
	"enabled"	INTEGER NOT NULL,
	"visible"	INTEGER NOT NULL,
	PRIMARY KEY("id")
);

-- API credentials.
CREATE TABLE "client_key" (
	"id"	TEXT NOT NULL,
	"salt"	TEXT,
	"hash"	TEXT,
	"site_id"	INTEGER NOT NULL,
	"secret"	TEXT NOT NULL,
	"name"	TEXT,
	"contact"	TEXT,
	"created"	INTEGER NOT NULL,
	"disabled"	INTEGER NOT NULL,
	PRIMARY KEY("id"),
	FOREIGN KEY("site_id") REFERENCES "site"("id")
);

-- Roles for API credentials.
CREATE TABLE "client_key_role" (
	"id"	INTEGER NOT NULL,
	"name"	TEXT NOT NULL UNIQUE,
	PRIMARY KEY("id")
);

-- Join table: API credentials x roles.
CREATE TABLE "client_key_client_key_roles" (
	"client_key_id"	TEXT NOT NULL,
	"client_key_role_id"	INTEGER NOT NULL,
	FOREIGN KEY("client_key_id") REFERENCES "client_key"("id"),
	FOREIGN KEY("client_key_role_id") REFERENCES "client_key_role"("id")
);

-- Better name would be site_type.
CREATE TABLE "client_type" (
	"id"	INTEGER NOT NULL,
	"name"	TEXT NOT NULL UNIQUE,
	PRIMARY KEY("id")
);

-- Join table: Sites x types.
CREATE TABLE "site_client_types" (
	"site_id"	INTEGER NOT NULL,
	"client_type_id"	INTEGER NOT NULL,
	FOREIGN KEY("client_type_id") REFERENCES "client_type"("id"),
	FOREIGN KEY("site_id") REFERENCES "site"("id")
);

-- Keysets for sharing.
CREATE TABLE "keyset" (
	"id"	INTEGER NOT NULL,
	"site_id"	INTEGER NOT NULL,
	"name"	TEXT NOT NULL,
	"created"	INTEGER NOT NULL,
	"enabled"	INTEGER NOT NULL,
	"default"	INTEGER NOT NULL,
	"share_with_all"	INTEGER NOT NULL,
	PRIMARY KEY("id")
);

-- Join table. If keyset.share_with_all is false, these are the sites that the
-- keys in a keyset can be shared with.
CREATE TABLE "keyset_allowed_sites" (
	"keyset_id"	INTEGER NOT NULL,
	"site_id"	INTEGER NOT NULL,
	FOREIGN KEY("keyset_id") REFERENCES "keyset"("id"),
	FOREIGN KEY("site_id") REFERENCES "site"("id")
);

-- Keys to encrypt raw UID2s into ad tokens.
-- These encryption keys belong to a keyset.
CREATE TABLE "keyset_encryption_key" (
	"id"	INTEGER NOT NULL,
	"keyset_id"	INTEGER NOT NULL,
	"secret"	TEXT NOT NULL,
	"created"	INTEGER NOT NULL,
	"activates"	INTEGER NOT NULL,
	"expires"	INTEGER NOT NULL,
	PRIMARY KEY("id"),
	FOREIGN KEY("keyset_id") REFERENCES "keyset"("id")
);

-- Keys to encrypt raw UID2s into ad tokens.
-- These keys do not belong to a keyset.
-- Could be merged into a single table.
CREATE TABLE "encryption_key" (
	"id"	INTEGER NOT NULL,
	"site_id"	INTEGER NOT NULL,
	"secret"	TEXT NOT NULL,
	"created"	INTEGER NOT NULL,
	"activates"	INTEGER NOT NULL,
	"expires"	INTEGER NOT NULL,
	PRIMARY KEY("id"),
	FOREIGN KEY("site_id") REFERENCES "site"("id")
);

-- Key pairs used to encrypt client-side API calls.
CREATE TABLE "client_side_keypair" (
	"id"	TEXT NOT NULL,
	"public_key"	TEXT NOT NULL,
	"private_key"	TEXT NOT NULL,
	"site_id"	INTEGER NOT NULL,
	"contact"	TEXT,
	"created"	INTEGER NOT NULL,
	"disabled"	INTEGER NOT NULL,
	"name"	TEXT,
	PRIMARY KEY("id"),
	FOREIGN KEY("site_id") REFERENCES "site"("id")
);

-- Synthetic sites.
INSERT INTO "site" ("id", "name", "description", "enabled", "visible") VALUES
    (-1, "Master", "", true, false),
    (-2, "Refresh", "", true, false),
    (2, "Fallback", "", true, false)
;

INSERT INTO "site" ("id", "name", "description", "enabled", "visible") SELECT
	json_extract(value, '$.id'),
	json_extract(value, '$.name'),
	json_extract(value, '$.description'),
	json_extract(value, '$.enabled'),
	coalesce(json_extract(value, '$.visible'), false)
FROM json_each(readfile('/data/sites.json'));

INSERT INTO "client_type" ("id", "name") VALUES
    (0, "DSP"),
    (1, "ADVERTISER"),
    (2, "DATA_PROVIDER"),
    (3, "PUBLISHER")
;

INSERT INTO site_client_types (site_id, client_type_id) SELECT
    json_extract(s.value, '$.id') as id,
    c.id
FROM
json_each(readfile('/data/sites.json')) as s,
json_each(json_extract(s.value, '$.clientTypes')) as t
LEFT OUTER JOIN client_type c ON t.value = c.name;

INSERT INTO client_key (id, salt, hash, site_id, secret, name, contact, created, disabled) SELECT
	json_extract(value, '$.key_id'),
	json_extract(value, '$.key_salt'),
	json_extract(value, '$.key_hash'),
	json_extract(value, '$.site_id'),
	json_extract(value, '$.secret'),
	json_extract(value, '$.name'),
	json_extract(value, '$.contact'),
	json_extract(value, '$.created'),
	json_extract(value, '$.disabled')
FROM json_each(readfile('/data/clients.json'));

INSERT INTO "client_key_role" ("id", "name") VALUES
    (0, "GENERATOR"),
    (1, "MAPPER"),
    (2, "ID_READER"),
    (3, "SHARER"),
    (4, "OPTOUT")
;

INSERT INTO client_key_client_key_roles (client_key_id, client_key_role_id) SELECT
    json_extract(c.value, '$.key_id') as id,
    r.id
FROM
json_each(readfile('/data/clients.json')) as c,
json_each(json_extract(c.value, '$.roles')) as cr
LEFT OUTER JOIN client_key_role r ON cr.value = r.name;

INSERT INTO "keyset" ("id", "site_id", "name", "created", "enabled", "default", "share_with_all") SELECT
    json_extract(value, '$.keyset_id'),
    json_extract(value, '$.site_id'),
    json_extract(value, '$.name'),
    json_extract(value, '$.created'),
    json_extract(value, '$.enabled'),
    json_extract(value, '$.default'),
    json_extract(value, '$.allowed_sites') IS NULL
FROM json_each(readfile('/data/keysets.json'));

INSERT INTO "keyset_encryption_key" (id, keyset_id, secret, created, activates, expires) SELECT
    json_extract(value, '$.id'),
    json_extract(value, '$.keyset_id'),
    json_extract(value, '$.secret'),
    json_extract(value, '$.created'),
    json_extract(value, '$.activates'),
    json_extract(value, '$.expires')
FROM json_each(readfile('/data/keyset_keys.json'));

INSERT INTO "encryption_key" ("id", "site_id", "secret", "created", "activates", "expires") SELECT
    json_extract(value, '$.id'),
    json_extract(value, '$.site_id'),
    json_extract(value, '$.secret'),
    json_extract(value, '$.created'),
    json_extract(value, '$.activates'),
    json_extract(value, '$.expires')
FROM json_each(readfile('/data/keys.json'));

INSERT INTO "keyset_allowed_sites" (keyset_id, site_id) SELECT
    json_extract(k.value, '$.keyset_id'),
    s.value
FROM
json_each(readfile('/data/keysets.json')) k,
json_each(json_extract(k.value, '$.allowed_sites')) s;

INSERT INTO client_side_keypair (id, public_key, private_key, site_id, contact, created, 'disabled', 'name') SELECT
    json_extract(value, '$.subscription_id'),
    json_extract(value, '$.public_key'),
    json_extract(value, '$.private_key'),
    json_extract(value, '$.site_id'),
    json_extract(value, '$.contact'),
    json_extract(value, '$.created'),
    json_extract(value, '$.disabled'),
    json_extract(value, '$.name')
FROM json_each(readfile('/data/client_side_keypairs.json'));
