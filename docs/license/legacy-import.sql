-- ─────────────────────────────────────────────────────────────────────────────────────────────
-- One-time import of the legacy jukeanator.com license data into the license server tables
-- (license_customer, license_machine). NOT a Flyway migration: run it by hand, once, at cutover.
--
-- Prerequisites
--   1. The jukeanator-engine schema exists (master instance started once, so Flyway created
--      license_customer / license_machine) and both tables are still empty.
--   2. A fresh dump of the legacy MariaDB database (jukeanat_jukeanator) is loaded into a scratch
--      schema on the same MySQL server:
--          CREATE DATABASE legacy_jukeanator;
--          mysql legacy_jukeanator < jukeanat_jukeanator_dump.sql
--   3. Run this script while connected to the jukeanator-engine schema:
--          mysql <engine_schema> < docs/license/legacy-import.sql
--
-- What it does
--   * CUSTOMER          -> license_customer   (passcode SHA-1 kept as-is; email lower-cased)
--   * CUSTOMER_SERIAL   -> license_machine    (paid-through quarter, location, build date)
--   * CUSTOMER_SERIAL_IP_ADDRESS is NOT carried over; its hit counts and last access dates are
--     folded into license_machine.heartbeat_count / first_seen_at / last_heartbeat_at.
--   * Repairs the rows where the legacy provisioning bug stored the license file path in
--     LOCATION_NAME and the quarter in BUILD_DATE.
--   * Marks the customers and machines that the legacy code revoked by hardcoded lists.
--   * Allocates ids from persistent_identity_seq (shared Hibernate id table) and advances it.
-- ─────────────────────────────────────────────────────────────────────────────────────────────

START TRANSACTION;

SELECT next_val INTO @base FROM persistent_identity_seq FOR UPDATE;
SELECT COALESCE(MAX(CUSTOMER_ID), 0) INTO @max_customer FROM legacy_jukeanator.CUSTOMER;

-- Customers: id = @base + CUSTOMER_ID
INSERT INTO license_customer (persistent_identity, customer_name, email, phone_number, address,
    notes, passcode_sha1, license_type, available_licenses, revoked, auto_created,
    legacy_customer_id, version, date_added, date_updated)
SELECT @base + c.CUSTOMER_ID,
       c.CUSTOMER_NAME,
       LOWER(TRIM(c.EMAIL)),
       NULLIF(TRIM(c.PHONE_NUMBER), ''),
       NULLIF(TRIM(c.ADDRESS), ''),
       NULLIF(TRIM(c.NOTES), ''),
       LOWER(TRIM(c.PASSCODE)),
       CASE WHEN UPPER(TRIM(c.LICENSE_TYPE)) IN ('R', 'A', 'C', 'P', 'L')
            THEN UPPER(TRIM(c.LICENSE_TYPE)) ELSE 'L' END,
       c.AVAILABLE_LICENSES,
       FALSE,
       -- Legacy heartbeats auto-created customers whose passcode is the SHA-1 of their email.
       LOWER(TRIM(c.PASSCODE)) = SHA1(TRIM(c.EMAIL)),
       c.CUSTOMER_ID,
       1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM legacy_jukeanator.CUSTOMER c;

-- Machines: id = @base + @max_customer + CUSTOMER_SERIAL_ID
INSERT INTO license_machine (persistent_identity, customer_id, serial_number, last_paid_quarter,
    location_name, build_date, revoked, client_kind, first_seen_at, last_heartbeat_at,
    heartbeat_count, legacy_customer_serial_id, version, date_added, date_updated)
SELECT @base + @max_customer + s.CUSTOMER_SERIAL_ID,
       @base + s.CUSTOMER_ID,
       TRIM(s.SERIAL_NUMBER),
       TRIM(s.LAST_PAID_QUARTER),
       CASE WHEN s.LOCATION_NAME LIKE '/var/www/%' THEN NULL
            ELSE NULLIF(TRIM(s.LOCATION_NAME), '') END,
       CASE WHEN s.LOCATION_NAME LIKE '/var/www/%' THEN NULL
            ELSE NULLIF(TRIM(s.BUILD_DATE), '') END,
       FALSE,
       'LEGACY',
       ip.first_access,
       ip.last_access,
       COALESCE(ip.hits, 0),
       s.CUSTOMER_SERIAL_ID,
       1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM legacy_jukeanator.CUSTOMER_SERIAL s
LEFT JOIN (
    SELECT CUSTOMER_SERIAL_ID,
           SUM(NUM_HITS) AS hits,
           TIMESTAMP(MIN(LAST_ACCESS_DATE)) AS first_access,
           TIMESTAMP(MAX(LAST_ACCESS_DATE)) AS last_access
    FROM legacy_jukeanator.CUSTOMER_SERIAL_IP_ADDRESS
    GROUP BY CUSTOMER_SERIAL_ID
) ip ON ip.CUSTOMER_SERIAL_ID = s.CUSTOMER_SERIAL_ID;

-- Legacy hardcoded revocations (ProvisionLicenseServerCommand, AscapServerCommand, MP3Jukeboxx).
UPDATE license_customer SET revoked = TRUE
WHERE email IN ('eftobias@gmail.com', 'retroeatery@hotmail.com', 'aguayluz2life@yahoo.com',
                'zurkeyon@gmail.com', 'keith.woods@live.com', 'peach4059@comcast.net',
                'flores.305@hotmail.com', 'creacarasucia@gmail.com', 'hsendelbach@gmail.com',
                'kenlemerond@gmail.com', 'vancort914@yahoo.com');

UPDATE license_machine SET revoked = TRUE
WHERE serial_number IN ('{1D38215A-A137-407B-ACFF-ED173A1C49D8}', '527546751', '672237337',
                        '{00DE7D4C-5001-408A-A965-DD0A79B94E8D}', '501911060', '553102140',
                        '531207125', '683133782', '532844453', '621041840', '590445152');

-- Advance the shared id sequence past everything just inserted.
UPDATE persistent_identity_seq
SET next_val = GREATEST(next_val,
    (SELECT COALESCE(MAX(persistent_identity), 0) + 1 FROM license_customer),
    (SELECT COALESCE(MAX(persistent_identity), 0) + 1 FROM license_machine));

COMMIT;

-- Sanity checks (compare with the legacy row counts).
SELECT (SELECT COUNT(*) FROM legacy_jukeanator.CUSTOMER) AS legacy_customers,
       (SELECT COUNT(*) FROM license_customer) AS customers,
       (SELECT COUNT(*) FROM legacy_jukeanator.CUSTOMER_SERIAL) AS legacy_serials,
       (SELECT COUNT(*) FROM license_machine) AS machines,
       (SELECT COUNT(*) FROM license_customer WHERE revoked) AS revoked_customers,
       (SELECT COUNT(*) FROM license_machine WHERE revoked) AS revoked_machines;
