# Master Mode and Database Configuration

This guide covers the MySQL database (optional for standalone and slave installs, always used in master mode), the setup of a master, and the build of the production master server on a Linux VPS (see "Production Server (Linux VPS)" at the end). The general PC setup (Java, media player, launch script, kiosk settings) is in [WindowsConfig.md](WindowsConfig.md) and [LinuxConfig.md](LinuxConfig.md). For building from source and running the tests, see [README.md](README.md).

## MySQL database

By default the application stores its data as files under `--app.data-dir` (`app.repository-type: filesystem`). It can store it in MySQL instead (`app.repository-type: jpa`); master mode always uses MySQL. MySQL 8.0 or later is required (the database is created below with the `utf8mb4_0900_ai_ci` collation, which earlier versions do not have); 8.4 LTS is recommended on Windows.

The application creates and updates its tables itself (Flyway, on startup), so only an empty database and a user are needed. It does not copy existing filesystem data into MySQL: after switching, the database starts empty.

### Windows

#### Step 1: Download

Open https://dev.mysql.com/downloads/mysql/ and select version **8.4 LTS** and **Microsoft Windows**, and download the **MSI Installer** (x64). The "No thanks, just start my download" link skips the Oracle account sign-up.

#### Step 2: Install and configure

Run the `.msi` and choose the **Typical** setup. At the end, leave **Run MySQL Configurator** ticked and click **Finish**. In the configurator:

- **Type and Networking:** keep **Development Computer** (or **Server Computer** for a master), port **3306**.
- **Accounts and Roles:** set a strong password for the MySQL `root` account and keep it somewhere safe.
- **Windows Service:** keep **Configure MySQL Server as a Windows Service** and **Start the MySQL Server at System Startup** ticked.
- Click **Execute**, then **Finish**.

#### Step 3: Create the database and user

Open **MySQL 8.4 Command Line Client** from the Start menu, enter the `root` password, and run (choose your own password in place of `INSERT_VALUE_HERE`):

```
CREATE DATABASE IF NOT EXISTS jukeanator CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'jukeanator'@'localhost' IDENTIFIED BY 'INSERT_VALUE_HERE';
GRANT ALL PRIVILEGES ON jukeanator.* TO 'jukeanator'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

A master that will also run the live MySQL integration tests needs the `jukeanator_test` database as well; see "Master mode MySQL setup" below.

#### Step 4: Set the password environment variable

In a Command Prompt, set the same password, then open a new Command Prompt:

```
setx DB_PASSWORD "INSERT_VALUE_HERE"
```

`DB_URL` (default `jdbc:mysql://localhost:3306/jukeanator`) and `DB_USERNAME` (default `jukeanator`) only need setting if MySQL runs on another host or port, or a different database or user name is used.

### Linux (Mint)

#### Step 1: Install

```
sudo apt update
sudo apt install mysql-server
```

This installs the MySQL version packaged for the Mint release (8.0 on Mint 21 and 22), which meets the requirement. The service starts automatically and is enabled at boot. Check it with `systemctl status mysql`.

#### Step 2: Secure the installation

```
sudo mysql_secure_installation
```

The first question asks whether to set up the VALIDATE PASSWORD component. Answer **N** to skip it, or **Y** to enforce password strength, in which case the `jukeanator` password chosen in Step 3 must meet the selected policy. Then answer **Y** to removing anonymous users, disallowing remote root login, removing the test database, and reloading the privilege tables. The script does not ask for a new `root` password: on Mint, the MySQL `root` account signs in through `sudo` rather than a password.

#### Step 3: Create the database and user

Open the MySQL prompt:

```
sudo mysql
```

and run (choose your own password in place of `INSERT_VALUE_HERE`):

```
CREATE DATABASE IF NOT EXISTS jukeanator CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'jukeanator'@'localhost' IDENTIFIED BY 'INSERT_VALUE_HERE';
GRANT ALL PRIVILEGES ON jukeanator.* TO 'jukeanator'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

A master that will also run the live MySQL integration tests needs the `jukeanator_test` database as well; see "Master mode MySQL setup" below.

#### Step 4: Set the password environment variable

Add this line to `~/.profile` with the same password (keep the file `chmod 600`), then log out and back in:

```
export DB_PASSWORD="INSERT_VALUE_HERE"
```

`DB_URL` (default `jdbc:mysql://localhost:3306/jukeanator`) and `DB_USERNAME` (default `jukeanator`) only need setting if MySQL runs on another host or port, or a different database or user name is used.

### Switch the application to MySQL

In the external `application.yml` (in `--app.config-dir`), set:

```
app:
  repository-type: jpa
```

Restart the application. On the first start it creates the tables; the log shows Flyway applying `V1__init_schema`. To go back to file storage, set `repository-type: filesystem` again.

To check the connection without the application, sign in as the new user and list the tables (after the first start). On Windows, run it from `C:\Program Files\MySQL\MySQL Server 8.4\bin`:

```
mysql -u jukeanator -p jukeanator -e "SHOW TABLES;"
```

## Master Mode

### Master mode environment variables

Keys and secrets are never put in the launch script. Set them permanently for the account that runs the application, the same way as the standalone/slave variables (see "Install JukeANator" in [WindowsConfig.md](WindowsConfig.md) or [LinuxConfig.md](LinuxConfig.md)). Variables shown with a default fall back to it when unset; the ones marked **required** have no usable default for a production master.

- `DISCOGS_CONSUMER_KEY` (default `dummy`): **Required.** Discogs API consumer key
- `DISCOGS_CONSUMER_SECRET` (default `dummy`): **Required.** Discogs API consumer secret
- `JWT_SECRET` (default blank: random key per run): **Required.** Signing key for web login
  tokens, 32+ random characters. When unset, every restart signs out all web users and tokens from
  other instances / `GeneratePostmanJwtToken` are rejected
- `DB_URL` (default `jdbc:mysql://localhost:3306/jukeanator`): JDBC URL of the MySQL database
- `DB_USERNAME` (default `jukeanator`): MySQL user
- `DB_PASSWORD` (default `password`): **Required.** MySQL password. Never leave the default in
  production
- `DB_DRIVER` (default `com.mysql.cj.jdbc.Driver`): JDBC driver class (normally left unset)
- `SMTP_HOST` (default `smtp.gmail.com`): outbound mail server
- `SMTP_PORT` (default `587`): outbound mail port (STARTTLS)
- `SMTP_USERNAME` (default blank): **Required.** SMTP login
- `SMTP_PASSWORD` (default blank): **Required.** SMTP password (for Gmail, an app password)
- `MAIL_FROM_ADDRESS` (default `receipt.from-address`, then the built-in address): sender address
  for verification emails, receipts and Financial Ledger summaries
- `BRAINTREE_MERCHANT_ID` (default blank): **Required.** Braintree merchant ID
- `BRAINTREE_PUBLIC_KEY` (default blank): **Required.** Braintree public key
- `BRAINTREE_PRIVATE_KEY` (default blank): **Required.** Braintree private key

The database variables apply whenever `app.repository-type` is `jpa`. The Braintree keys must match `braintree.environment` in `application.yml` (`sandbox` or `production`), which is set there rather than through an environment variable.

A variable only takes effect if the external `application.yml` (in `--app.config-dir`) keeps that property in its `${VARIABLE:default}` form. A literal value written there overrides it.

**Generate a JWT secret.** On Linux:

```
openssl rand -base64 48
```

On Windows (Windows does not include `openssl`), in a Command Prompt:

```
powershell -Command "$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)"
```

Both print a random 64-character value to use as `JWT_SECRET`.

Windows example (one `setx` per variable, then open a new Command Prompt):

```
setx DISCOGS_CONSUMER_KEY "INSERT_VALUE_HERE"
setx DISCOGS_CONSUMER_SECRET "INSERT_VALUE_HERE"
setx JWT_SECRET "INSERT_VALUE_HERE"
setx DB_PASSWORD "INSERT_VALUE_HERE"
setx SMTP_USERNAME "INSERT_VALUE_HERE"
setx SMTP_PASSWORD "INSERT_VALUE_HERE"
setx BRAINTREE_MERCHANT_ID "INSERT_VALUE_HERE"
setx BRAINTREE_PUBLIC_KEY "INSERT_VALUE_HERE"
setx BRAINTREE_PRIVATE_KEY "INSERT_VALUE_HERE"
```

To set them for all users instead, run an **Administrator** Command Prompt and add `/M` to each command.

Linux example (add to `~/.profile` with a text editor, keep it `chmod 600`, then log out and back in):

```
# JukeANator (master mode)
export DISCOGS_CONSUMER_KEY="INSERT_VALUE_HERE"
export DISCOGS_CONSUMER_SECRET="INSERT_VALUE_HERE"
export JWT_SECRET="INSERT_VALUE_HERE"
export DB_URL="jdbc:mysql://localhost:3306/jukeanator"
export DB_USERNAME="jukeanator"
export DB_PASSWORD="INSERT_VALUE_HERE"
export SMTP_HOST="smtp.gmail.com"
export SMTP_PORT="587"
export SMTP_USERNAME="INSERT_VALUE_HERE"
export SMTP_PASSWORD="INSERT_VALUE_HERE"
export MAIL_FROM_ADDRESS="INSERT_VALUE_HERE"
export BRAINTREE_MERCHANT_ID="INSERT_VALUE_HERE"
export BRAINTREE_PUBLIC_KEY="INSERT_VALUE_HERE"
export BRAINTREE_PRIVATE_KEY="INSERT_VALUE_HERE"
```

### Master mode MySQL setup

Install MySQL as described under "MySQL database" above, then open the MySQL prompt as `root` (the **MySQL 8.4 Command Line Client** on Windows, `sudo mysql` on Linux Mint) and run the statements below. Besides the `jukeanator` database, they create the `jukeanator_test` database used by the live MySQL integration tests; skip the `jukeanator_test` lines on a production master that never runs the tests.

```
CREATE DATABASE IF NOT EXISTS jukeanator CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS jukeanator_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'jukeanator'@'localhost' IDENTIFIED BY 'INSERT_VALUE_HERE';
GRANT ALL PRIVILEGES ON jukeanator.* TO 'jukeanator'@'localhost';
GRANT ALL PRIVILEGES ON jukeanator_test.* TO 'jukeanator'@'localhost';
FLUSH PRIVILEGES;
```

Use the same password as `DB_PASSWORD`. The tests connect with `DB_PASSWORD` too, falling back to `password` when it is unset, so on a development machine without `DB_PASSWORD` the user's password must be `password`. On a production master, always use a strong password and set `DB_PASSWORD`.

Then switch the application to MySQL as described under "Switch the application to MySQL" above.

### Remote database connections

Only if the application connects to MySQL from another computer, also create a user for remote connections (and allow remote connections in the MySQL server's `bind-address` setting):

```
CREATE USER IF NOT EXISTS 'jukeanator'@'%' IDENTIFIED BY 'INSERT_VALUE_HERE';
GRANT ALL PRIVILEGES ON jukeanator.* TO 'jukeanator'@'%';
FLUSH PRIVILEGES;
```

On the computer running the application, set `DB_URL` to point at the MySQL server, for example `jdbc:mysql://192.168.1.10:3306/jukeanator`.

### Resetting the databases

To start over with empty databases (this permanently deletes all of their data), drop them and then run the `CREATE DATABASE` statements above again:

```
DROP DATABASE IF EXISTS jukeanator;
DROP DATABASE IF EXISTS jukeanator_test;
```

## Production Server (Linux VPS)

This section records how the production master was built, so it can be rebuilt on another server. The first server is an unmanaged Mochahost "Frappe Linux I" VPS running Ubuntu 24.04, at IP address `65.98.125.113` (host name `app.jukeanator.com`). Replace that address with the new server's address throughout.

The finished server looks like this:

- **nginx** listens on port 80 (and 443 once a certificate exists) and forwards every request to the application on `127.0.0.1:8080`.
- **The application** (the WAR, run with `java -jar`) runs as the systemd service `jukeanator`, under its own `jukeanator` system account.
- **MySQL 8.0** accepts local connections only.
- **Postfix** sends the application's email and forwards mail sent to `support@jukeanator.com` to a personal mailbox.
- **A nightly timer** backs up the database.
- **The firewall** (`ufw`) allows only ports 22, 80, 443 and 25 in.

Files and folders:

- `/opt/jukeanator/jukeanator-engine.war`: the application. `jukeanator-engine.war.previous` is the version before the last update.
- `/etc/jukeanator/jukeanator.env`: passwords and API keys (owner `root`, group `jukeanator`, mode 640).
- `/var/lib/jukeanator`: the data folder (`--app.data-dir`), owned by `jukeanator`.
- `/var/lib/jukeanator/config/application.yml`: the configuration (`--app.config-dir`). It must be writable by the application, which saves `.bak` copies there when it merges in new settings. This is why it is not under `/etc`.
- `/var/lib/jukeanator/downloads/temp`: legacy client downloads (`MP3Jukeboxx.jar`, `kiosk.zip`).
- `/var/log/jukeanator`: the application log.
- `/var/backups/jukeanator`: nightly database backups (30 days kept).

Secret values (database password, JWT secret, Braintree and Discogs keys) never go into this guide, into git or into chat. The database password and JWT secret are generated on the server; the API keys are typed in by the operator (Step 7). Keep the VPS root password for the hosting provider's console only; day-to-day access uses an SSH key.

Unless a step says otherwise, the commands run on the server as `root`.

### Step 1: SSH key access

On the Windows PC that will administer the server, create a key (press Enter for no passphrase, or choose one):

```
ssh-keygen -t ed25519 -f %USERPROFILE%\.ssh\jukeanator_prod
```

Install the public key on the server. This uses the root password one time:

```
type %USERPROFILE%\.ssh\jukeanator_prod.pub | ssh root@65.98.125.113 "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
```

Add a short name for the server to `%USERPROFILE%\.ssh\config`:

```
Host jukeanator-prod
  HostName 65.98.125.113
  User root
  IdentityFile ~/.ssh/jukeanator_prod
```

Check that `ssh jukeanator-prod` signs in without a password. Then turn off password sign-in on the server by creating `/etc/ssh/sshd_config.d/00-hardening.conf`:

```
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
```

and reload SSH:

```
sshd -t && systemctl reload ssh
```

Keep the current session open and check in a second window that `ssh jukeanator-prod` still works before closing it. If the key is ever lost, the provider's web console still accepts the root password.

To set up SSH on a new or replacement PC (with or without a backup of the key), and to browse the production database in DBeaver, see [docs/production-database-access.md](docs/production-database-access.md).

### Step 2: Base system

Update the system, keep the clock in UTC, and turn on automatic security updates:

```
apt update && apt -y full-upgrade
timedatectl set-timezone Etc/UTC
apt -y install unattended-upgrades fail2ban
dpkg-reconfigure -f noninteractive unattended-upgrades
```

`/etc/apt/apt.conf.d/20auto-upgrades` should then contain:

```
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
```

fail2ban needs no extra configuration: its default `sshd` jail blocks addresses that keep failing to sign in. Check it with `fail2ban-client status`.

Add a 2 GB swap file (the VPS has little memory, and swap keeps a memory spike from killing MySQL or Java):

```
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf
sysctl --system
```

Turn on the firewall. Port 25 is for incoming mail to `support@jukeanator.com`:

```
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 25/tcp
ufw enable
ufw status verbose
```

### Step 3: Java, MySQL and nginx

```
apt -y install openjdk-21-jre-headless mysql-server nginx
```

The application is built for Java 21. A headless runtime is enough, because a master runs without the Swing UI.

On Ubuntu, MySQL already listens on `127.0.0.1` only. Check that `/etc/mysql/mysql.conf.d/mysqld.cnf` contains:

```
bind-address		= 127.0.0.1
mysqlx-bind-address	= 127.0.0.1
```

MySQL's `root` account signs in through the system's `root` user (no password); the backup in Step 12 relies on this.

### Step 4: Database and user

Generate a password for the `jukeanator` MySQL user and keep it for Step 7:

```
openssl rand -base64 24
```

Open the MySQL prompt with `mysql` and create the database and user as shown in "Master mode MySQL setup" above, without the `jukeanator_test` lines (production never runs the tests).

### Step 5: Service account and folders

```
useradd --system --home-dir /var/lib/jukeanator --shell /usr/sbin/nologin jukeanator
mkdir -p /opt/jukeanator /etc/jukeanator /var/log/jukeanator
mkdir -p /var/lib/jukeanator/config /var/lib/jukeanator/downloads/temp
chown -R jukeanator:jukeanator /var/lib/jukeanator /var/log/jukeanator
chmod 750 /var/lib/jukeanator/config /var/lib/jukeanator/downloads /var/log/jukeanator
chown root:jukeanator /etc/jukeanator
chmod 750 /etc/jukeanator
```

The account cannot sign in; it only runs the application.

### Step 6: Configuration file

Copy `docs/application-master-mode.yml` from the project to the server (run this on the PC, from the project folder):

```
scp docs/application-master-mode.yml jukeanator-prod:/var/lib/jukeanator/config/application.yml
```

Then edit it on the server with `nano /var/lib/jukeanator/config/application.yml` and make these changes:

- `app.data-dir`: `/var/lib/jukeanator`
- the service log `file-path`: `/var/log/jukeanator/jukeanator-service.log`
- `license-server.downloads-dir`: `/var/lib/jukeanator/downloads`
- `email.verification-base-url`: `http://65.98.125.113` until the domain points at the server, then `https://www.jukeanator.com`
- under `spring.mail.properties.mail.smtp`: `auth: false` and `starttls.enable: false`, because the application sends through the local Postfix (Step 11), which needs no login
- the sender and recipient addresses (`from-address`, `financial-ledger-recipients`, `feedback-recipients`): `support@jukeanator.com`
- optional, `location.private-locations`: locations that only the listed accounts may see, for example `1: [tmyers1@yahoo.com]` for a test location. A private location is never selected by default; the web UI selects the only public location when it is online

Add this block at the end, so the application only accepts connections from nginx on the same server:

```
server:
  address: 127.0.0.1
  port: 8080
  forward-headers-strategy: native
```

`forward-headers-strategy: native` makes the application use the visitor's real address and `http`/`https` from the headers nginx adds.

Leave the database, mail server and key settings in their `${VARIABLE:default}` form; their values come from Step 7. Finally:

```
chown jukeanator:jukeanator /var/lib/jukeanator/config/application.yml
chmod 640 /var/lib/jukeanator/config/application.yml
```

### Step 7: Passwords and API keys

Create `/etc/jukeanator/jukeanator.env` with `nano`. It holds one `NAME=value` per line, without quotes:

```
DB_URL=jdbc:mysql://localhost:3306/jukeanator
DB_USERNAME=jukeanator
DB_PASSWORD=INSERT_VALUE_HERE
JWT_SECRET=INSERT_VALUE_HERE
SMTP_HOST=127.0.0.1
SMTP_PORT=25
BRAINTREE_MERCHANT_ID=REPLACE_ME
BRAINTREE_PUBLIC_KEY=REPLACE_ME
BRAINTREE_PRIVATE_KEY=REPLACE_ME
DISCOGS_CONSUMER_KEY=REPLACE_ME
DISCOGS_CONSUMER_SECRET=REPLACE_ME
```

`DB_PASSWORD` is the password from Step 4. Generate `JWT_SECRET` with `openssl rand -base64 48`. The application refuses to start without some Braintree value, so `REPLACE_ME` placeholders let it start before the real keys are entered.

```
chown root:jukeanator /etc/jukeanator/jukeanator.env
chmod 640 /etc/jukeanator/jukeanator.env
```

To enter the Braintree and Discogs keys without typing them into a file or a chat, create the helper script `/usr/local/sbin/jukeanator-set-secrets` with the content below, then `chmod 750 /usr/local/sbin/jukeanator-set-secrets`. It asks for each key without showing it, strips spaces and stray characters picked up by copy and paste, saves the file (keeping a `.bak`), shows each key's length, and restarts the application.

```
#!/bin/bash
# Prompts for the JukeANator API keys and writes them to /etc/jukeanator/jukeanator.env, then
# restarts the app. Press Enter at a prompt to keep the current value. Typed values are not shown;
# leading/trailing spaces and line breaks picked up by copy/paste are removed automatically.
# For testing, ENV_FILE overrides the file and NO_RESTART=1 skips the restart.
set -uo pipefail
F=${ENV_FILE:-/etc/jukeanator/jukeanator.env}
VARS="BRAINTREE_MERCHANT_ID BRAINTREE_PUBLIC_KEY BRAINTREE_PRIVATE_KEY DISCOGS_CONSUMER_KEY DISCOGS_CONSUMER_SECRET"
cp -p "$F" "$F.bak"
changed=0
for v in $VARS; do
  while true; do
    val=""
    read -r -s -p "$v (Enter = keep current): " val || true
    echo
    # Drop terminal key sequences (arrow keys, paste markers: ESC [ ... letter or ~), then other
    # control characters (line breaks, tabs) and outer spaces.
    val=$(printf '%s' "$val" | sed -e 's/\x1b\[[0-9;]*[A-Za-z~]//g' -e 's/\x1bO[A-Za-z]//g' \
      | tr -d '\000-\037\177' | sed -e 's/^ *//' -e 's/ *$//')
    if [ -z "$val" ]; then echo "  kept current value"; break; fi
    if [[ "$val" == *" "* || "$val" == *'"'* || "$val" == *"'"* ]]; then
      echo "  rejected: the value contains a space or quote in the middle; try again"
      continue
    fi
    tmp=$(mktemp)
    grep -vE "^#?${v}=" "$F" > "$tmp" || true
    printf '%s=%s\n' "$v" "$val" >> "$tmp"
    cat "$tmp" > "$F"; rm -f "$tmp"
    changed=1; echo "  saved (${#val} characters)"
    break
  done
done
if [ -z "${ENV_FILE:-}" ]; then chown root:jukeanator "$F"; chmod 640 "$F"; fi
echo
for v in $VARS; do
  line=$(grep -E "^${v}=" "$F" | tail -1 || true)
  value=${line#*=}
  if [ -z "$line" ]; then s="missing"; elif [ "$value" = "REPLACE_ME" ]; then s="placeholder"
  else s="set (${#value} characters)"; fi
  printf '%-26s %s\n' "$v" "$s"
done
if [ "$changed" = 1 ] && [ "${NO_RESTART:-0}" != 1 ]; then
  echo; echo "Restarting the app..."
  systemctl restart jukeanator
  for i in $(seq 1 40); do sleep 3; curl -s -o /dev/null --max-time 3 http://127.0.0.1:8080/ && break; done
  echo "app: $(systemctl is-active jukeanator)"
fi
```

Run it from the PC, once the application is installed (Step 9):

```
ssh -t jukeanator-prod jukeanator-set-secrets
```

The Braintree keys must match `braintree.environment` in `application.yml` (`sandbox` for testing, `production` for real payments).

### Step 8: Legacy downloads

Legacy clients download updates from `/temp/**`. Copy `MP3Jukeboxx.jar` and `kiosk.zip` (they are not in git) from the PC, keeping their original dates with `-p` (run on the PC, from the folder holding them):

```
scp -p MP3Jukeboxx.jar kiosk.zip jukeanator-prod:/var/lib/jukeanator/downloads/temp/
```

Then on the server:

```
chown -R jukeanator:jukeanator /var/lib/jukeanator/downloads
```

The jar's date must stay exactly as it was: legacy clients compare it with their own build date and re-download on any difference (see "Downloads folder" in [docs/license-server-deployment.md](docs/license-server-deployment.md)). If the date was lost, restore it with `touch -d`; the first server uses `touch -d '2023-02-04 15:47:53 UTC' MP3Jukeboxx.jar`. `curl "http://65.98.125.113/FrontController?command=getJukeANatorBuildDate"` must then print `02-04-2023`.

### Step 9: Install the application as a service

Build the WAR on the PC, from the project folder (the tests are run separately):

```
mvnw -DskipTests package
```

Copy it to the server:

```
scp target/jukeanator-engine-0.0.1-SNAPSHOT.war jukeanator-prod:/opt/jukeanator/jukeanator-engine.war
```

On the server, run `chmod 644 /opt/jukeanator/jukeanator-engine.war` and create `/etc/systemd/system/jukeanator.service`:

```
[Unit]
Description=JukeANator master instance
After=network-online.target mysql.service
Wants=network-online.target
Requires=mysql.service

[Service]
Type=simple
User=jukeanator
Group=jukeanator
WorkingDirectory=/var/lib/jukeanator
EnvironmentFile=/etc/jukeanator/jukeanator.env
ExecStart=/usr/bin/java -Xms256m -Xmx1g -Djava.awt.headless=true -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -jar /opt/jukeanator/jukeanator-engine.war --app.config-dir=/var/lib/jukeanator/config --app.data-dir=/var/lib/jukeanator
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
TimeoutStopSec=60
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ReadWritePaths=/var/lib/jukeanator /var/log/jukeanator

[Install]
WantedBy=multi-user.target
```

Start it and enable it at boot:

```
systemctl daemon-reload
systemctl enable --now jukeanator
journalctl -u jukeanator -f
```

The first start takes about a minute; Flyway creates the tables. Press Ctrl+C to stop following the log. `curl -I http://127.0.0.1:8080/` must answer `200`.

### Step 10: nginx

Remove the default site and create `/etc/nginx/sites-available/jukeanator`:

```
map $http_upgrade $connection_upgrade {
    default upgrade;
    ""      close;
}

upstream jukeanator_app {
    server 127.0.0.1:8080;
    keepalive 16;
}

server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    client_max_body_size 50m;
    server_tokens off;

    access_log /var/log/nginx/jukeanator.access.log;
    error_log  /var/log/nginx/jukeanator.error.log;

    location / {
        proxy_pass http://jukeanator_app;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host  $host;
        proxy_set_header X-Forwarded-Port  $server_port;
        proxy_set_header Upgrade           $http_upgrade;
        proxy_set_header Connection        $connection_upgrade;
        proxy_read_timeout 3600s;   # long-lived WebSocket connections (/ws, /ws-slave)
        proxy_send_timeout 3600s;
    }
}
```

```
rm -f /etc/nginx/sites-enabled/default
ln -s /etc/nginx/sites-available/jukeanator /etc/nginx/sites-enabled/jukeanator
nginx -t && systemctl reload nginx
```

The `Upgrade` and `Connection` headers and the one-hour timeouts keep the WebSocket connections from the web UI and the slaves open. Do not add a redirect from HTTP to HTTPS: legacy clients call `/FrontController` and `/temp/**` over plain HTTP and cannot follow a redirect.

The web UI is now at `http://65.98.125.113/`.

### Step 11: Mail

Postfix sends the application's email directly to the recipients' mail servers. It also receives mail for `support@jukeanator.com`, `postmaster@jukeanator.com` and `abuse@jukeanator.com` and forwards it to a personal mailbox. PostSRSd rewrites the sender of forwarded mail, so the receiving mailbox does not reject it as forged. OpenDKIM signs outgoing mail.

Do not use the mail server of the old shared hosting plan.

**Install.** Answer the Postfix questions in advance so the installer does not stop to ask:

```
echo "postfix postfix/main_mailer_type select Internet Site" | debconf-set-selections
echo "postfix postfix/mailname string jukeanator.com" | debconf-set-selections
apt -y install postfix postsrsd opendkim opendkim-tools mailutils
```

**Postfix settings:**

```
postconf -e 'myhostname = app.jukeanator.com'
postconf -e 'mydomain = jukeanator.com'
postconf -e 'mydestination = $myhostname, localhost.localdomain, localhost'
postconf -e 'mynetworks = 127.0.0.0/8'
postconf -e 'inet_interfaces = all'
postconf -e 'inet_protocols = ipv4'
postconf -e 'smtpd_helo_required = yes'
postconf -e 'disable_vrfy_command = yes'
postconf -e 'smtpd_recipient_restrictions = permit_mynetworks, reject_unauth_destination, reject_rbl_client zen.spamhaus.org=127.0.0.[2..11]'
postconf -e 'virtual_alias_domains = jukeanator.com'
postconf -e 'virtual_alias_maps = hash:/etc/postfix/virtual'
postconf -e 'sender_canonical_maps = tcp:127.0.0.1:10001'
postconf -e 'sender_canonical_classes = envelope_sender'
postconf -e 'recipient_canonical_maps = tcp:127.0.0.1:10002'
postconf -e 'recipient_canonical_classes = envelope_recipient, header_recipient'
postconf -e 'smtpd_milters = inet:127.0.0.1:8891'
postconf -e 'non_smtpd_milters = $smtpd_milters'
postconf -e 'milter_default_action = accept'
```

`myhostname` must match the server's reverse DNS name (ask the provider to set the IP address's reverse DNS to `app.jukeanator.com`). `mynetworks` limits sending to programs on this server, so the server cannot be used to relay spam.

**Forwarding.** Create `/etc/postfix/virtual`:

```
support@jukeanator.com     tmyers1@yahoo.com
postmaster@jukeanator.com  tmyers1@yahoo.com
abuse@jukeanator.com       tmyers1@yahoo.com
```

and send the server's own messages (for example a failed backup) to the same mailbox by adding `root: tmyers1@yahoo.com` to `/etc/aliases`. Then:

```
postmap /etc/postfix/virtual
newaliases
```

**PostSRSd.** In `/etc/default/postsrsd`, set:

```
SRS_DOMAIN=jukeanator.com
SRS_EXCLUDE_DOMAINS=jukeanator.com,app.jukeanator.com
SRS_LISTEN_ADDR=127.0.0.1
SRS_FORWARD_PORT=10001
SRS_REVERSE_PORT=10002
```

**OpenDKIM.** Create the signing key:

```
mkdir -p /etc/opendkim/keys/jukeanator.com
opendkim-genkey -b 2048 -d jukeanator.com -s s1 -D /etc/opendkim/keys/jukeanator.com
chown -R opendkim:opendkim /etc/opendkim/keys
chmod 600 /etc/opendkim/keys/jukeanator.com/*
```

Set these lines in `/etc/opendkim.conf`:

```
UMask             007
Socket            inet:8891@127.0.0.1
Mode              sv
Domain            jukeanator.com
Selector          s1
KeyFile           /etc/opendkim/keys/jukeanator.com/s1.private
Canonicalization  relaxed/simple
OversignHeaders   From
InternalHosts     127.0.0.1, ::1, localhost
```

and set `SOCKET=inet:8891@127.0.0.1` in `/etc/default/opendkim`. Then restart all three:

```
systemctl restart postsrsd opendkim postfix
```

**DNS records.** Mail is only accepted by Yahoo, Gmail and others once the domain's DNS has these records (the domain's DNS host adds them):

- `A` record `app` pointing to `65.98.125.113`
- SPF: `TXT` record on `jukeanator.com` with `v=spf1 a mx ip4:65.98.125.113 ~all`
- DKIM: `TXT` record `s1._domainkey` with the value printed by `cat /etc/opendkim/keys/jukeanator.com/s1.txt` (the parts in quotes, joined)
- DMARC: `TXT` record `_dmarc` with `v=DMARC1; p=none`
- an `MX` record for `jukeanator.com` pointing to `app.jukeanator.com`, only once support mail should arrive at this server instead of the old host

Until then, Yahoo rejects the server's mail with `550 5.7.25 Forward-confirmed reverse DNS failed`. Once the records exist, test with:

```
echo "Test from the JukeANator server" | mail -s "Test" tmyers1@yahoo.com
tail -f /var/log/mail.log
```

### Step 12: Nightly database backup

Create `/usr/local/sbin/jukeanator-db-backup` and `chmod 750` it:

```
#!/bin/bash
set -euo pipefail
DIR=/var/backups/jukeanator
KEEP_DAYS=30
umask 077
mkdir -p "$DIR"
STAMP=$(date +%Y-%m-%d_%H%M)
FINAL="$DIR/jukeanator_$STAMP.sql.gz"
PART="$DIR/.jukeanator_$STAMP.sql.gz.part"
trap 'rm -f "$PART"' EXIT
mysqldump --single-transaction --quick --routines --triggers --events --no-tablespaces jukeanator | gzip -9 > "$PART"
gzip -t "$PART"
mv "$PART" "$FINAL"
find "$DIR" -maxdepth 1 -name "jukeanator_*.sql.gz" -mtime +"$KEEP_DAYS" -delete
echo "Backup written: $FINAL ($(du -h "$FINAL" | cut -f1)); $(ls "$DIR"/jukeanator_*.sql.gz | wc -l) backups kept"
```

It runs as `root`, which MySQL lets in without a password. `--single-transaction` takes a consistent copy without stopping the application.

Create `/etc/systemd/system/jukeanator-db-backup.service`:

```
[Unit]
Description=JukeANator nightly MySQL backup
After=mysql.service
Requires=mysql.service
OnFailure=jukeanator-db-backup-failed.service

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/jukeanator-db-backup
Nice=10
IOSchedulingClass=idle
```

`/etc/systemd/system/jukeanator-db-backup-failed.service`, which emails the last log lines to `root` (forwarded in Step 11) when a backup fails:

```
[Unit]
Description=Email root when the JukeANator database backup fails

[Service]
Type=oneshot
ExecStart=/bin/sh -c "journalctl -u jukeanator-db-backup.service -n 40 --no-pager | mail -s \"JukeANator database backup FAILED on app.jukeanator.com\" root"
```

and `/etc/systemd/system/jukeanator-db-backup.timer`:

```
[Unit]
Description=Run the JukeANator database backup nightly at 3:30 AM Detroit time

[Timer]
OnCalendar=*-*-* 03:30:00 America/Detroit
Persistent=true

[Install]
WantedBy=timers.target
```

Enable the timer and run one backup now:

```
systemctl daemon-reload
systemctl enable --now jukeanator-db-backup.timer
systemctl start jukeanator-db-backup.service
journalctl -u jukeanator-db-backup.service -n 5
ls -l /var/backups/jukeanator
```

`Persistent=true` runs a missed backup at the next boot. The backups stay on the same server, so they do not protect against losing the server itself; copy one to another computer from time to time with `scp jukeanator-prod:/var/backups/jukeanator/jukeanator_<date>.sql.gz .`

**Restore.** Stop the application, load the backup, and start it again:

```
systemctl stop jukeanator
gunzip -c /var/backups/jukeanator/jukeanator_<date>.sql.gz | mysql jukeanator
systemctl start jukeanator
```

To check a backup without touching the live data, load it into a scratch database (`CREATE DATABASE restore_test;`, then `gunzip -c ... | mysql restore_test`), compare row counts, and drop it.

### Step 13: Data

The new database starts empty apart from what the application creates.

**Existing master data (optional).** To move an existing master's data, export its `jukeanator` database with a tool such as DBeaver or `mysqldump`, leaving out the `license_customer` and `license_machine` tables if the legacy license data is imported below. Copy the file to the server and load it with the application stopped:

```
systemctl stop jukeanator
mysql jukeanator < /root/import/master-export.sql
systemctl start jukeanator
```

Both databases must have the same schema version, so the export must come from the same build of the application.

**Legacy license data.** Load the legacy `jukeanator.com` database dump into a scratch database and run the import script (details in [docs/license/legacy-import.sql](docs/license/legacy-import.sql) and "Cutover" in [docs/license-server-deployment.md](docs/license-server-deployment.md)):

```
mysql -e "CREATE DATABASE legacy_jukeanator"
mysql legacy_jukeanator < /root/import/legacy-dump.sql
mysql jukeanator < /root/import/legacy-import.sql
mysql -e "DROP DATABASE legacy_jukeanator"
```

Restart the application afterwards (`systemctl restart jukeanator`), and delete the dump files from `/root/import` once the import is checked.

### Step 14: Accounts

Accounts are created through the web UI (**Create Account**). Until the DNS records exist, verification emails do not arrive, so a new account can be activated and given credits directly in the database instead.

The application keeps all accounts in memory, so changes made in the database are only seen after a restart. Stop the application first, make the change, then start it:

```
systemctl stop jukeanator
mysql jukeanator
```

To activate an account:

```
UPDATE user_account
   SET status = 'ACTIVE', verification_token_hash = NULL, verification_token_expires_at = NULL,
       version = version + 1, date_updated = NOW()
 WHERE email_address = 'guest@example.com';
```

Credits belong to one location (see [docs/per-location-accounts.md](docs/per-location-accounts.md)), so there is no account-wide balance to set. The account's balance at a location is in `user_location_account`, That row is created the first time the signed-in account selects the location in the web UI (loading its home page creates it). To give a test account credits at a location:

```
UPDATE user_location_account a
  JOIN user_account u ON u.persistent_identity = a.user_id
   SET a.num_credits = 99, a.version = a.version + 1, a.date_updated = NOW()
 WHERE u.email_address = 'guest@example.com' AND a.location_id = 2;
```

Credits given this way have no Add Funds purchase behind them, so they never appear in the location's financial ledger. Use them for testing only.

To make an account an administrator (needed for `/admin/licenses.html`):

```
UPDATE user_account SET role = 'ROLE_ADMIN', version = version + 1, date_updated = NOW()
 WHERE email_address = 'tmyers1@yahoo.com';
```

Then `EXIT;` and `systemctl start jukeanator`.

Use `example.com` addresses for test accounts, so no email ever goes to a real person. Disable or re-password test accounts before going live.

### Step 15: Check the server

From the PC:

```
curl -I http://65.98.125.113/
curl "http://65.98.125.113/FrontController?command=getJukeANatorBuildDate"
curl "http://65.98.125.113/api/locations"
```

The first must answer `200`, the second must print the legacy jar's date (Step 8), and the third lists the public locations as JSON. Then sign in to the web UI, open `/admin/licenses.html` with an administrator account, and point a test kiosk at the server (`app.master-instance-url: http://65.98.125.113` in its `application.yml`) to check that it shows as online.

On the server, check that only nginx, SSH and Postfix accept outside connections:

```
ss -tlnp
```

MySQL (3306, 33060), the application (8080), OpenDKIM (8891) and PostSRSd (10001, 10002) must show `127.0.0.1` only.

### Updating the application

**Automated (recommended).** From the project folder on the PC, run:

```
mvnw clean deploy -Pprod
```

This runs the tests, builds the WAR and deploys it (add `-DskipTests` to skip the tests); a failing test stops it before anything is deployed. The `prod` profile in `pom.xml` skips Maven's own install and deploy steps and instead runs `deploy\deploy-prod.ps1 -SkipBuild` in the deploy phase. The script can also be run on its own, which builds the WAR without the tests first:

```
powershell -ExecutionPolicy Bypass -File deploy\deploy-prod.ps1
```

Either way it needs only the SSH key access from Step 1; no passwords or secrets are involved, and nothing secret is kept in git. The script:

- builds the WAR (`mvnw -DskipTests package`), unless run with `-SkipBuild` as Maven does;
- uploads it to the server as `jukeanator-engine.war.new`, together with `deploy/jukeanator-deploy.sh`, and runs that script on the server;
- on the server: checks the upload against the SHA-256 computed on the PC, runs the database backup (Step 12), keeps the current WAR as `jukeanator-engine.war.previous`, switches to the new one, restarts the service and waits up to four minutes for the application to answer;
- if the new version does not answer in time, puts the previous WAR back, restarts it, prints the last log lines and reports the deploy as failed.

Options:

- `-SkipBuild` deploys the WAR already in `target\` without building.
- `-SshHost <name>` (script only), or the `JUKEANATOR_DEPLOY_HOST` environment variable (script and Maven), deploys to another server's entry in `%USERPROFILE%\.ssh\config` (default `jukeanator-prod`). This is how to deploy to a rebuilt or second server.

The automatic rollback only restores the WAR. If the new version already changed the database schema before failing, also restore the backup the script took (the newest file in `/var/backups/jukeanator`, see "Restore" in Step 12).

**By hand.** Build the WAR on the PC (`mvnw -DskipTests package`) and copy it to the server under a temporary name:

```
scp target/jukeanator-engine-0.0.1-SNAPSHOT.war jukeanator-prod:/opt/jukeanator/jukeanator-engine.war.new
```

Then on the server keep the current version and switch to the new one:

```
cd /opt/jukeanator
cp -p jukeanator-engine.war jukeanator-engine.war.previous
mv jukeanator-engine.war.new jukeanator-engine.war
chmod 644 jukeanator-engine.war
systemctl restart jukeanator
journalctl -u jukeanator -f
```

To go back to the previous version:

```
cd /opt/jukeanator
cp -p jukeanator-engine.war.previous jukeanator-engine.war
systemctl restart jukeanator
```

A version that changed the database schema cannot simply be rolled back; restore the backup taken before the update as well (Step 12).

### Everyday commands

- `systemctl status jukeanator`: whether the application is running
- `systemctl restart jukeanator`: restart it (for example after editing `application.yml`)
- `journalctl -u jukeanator -n 200`: the last 200 log lines (`-f` to follow)
- `/var/log/jukeanator/jukeanator-service.log`: the application's own log file
- `/var/log/nginx/jukeanator.access.log`: every web request
- `/var/log/mail.log`: sent and received mail
- `systemctl list-timers jukeanator-db-backup.timer`: when the next backup runs
- `fail2ban-client status sshd`: addresses blocked for failed SSH sign-ins

### Still to do when the domain moves to the server

These steps happen at the switch from the old hosting plan (see also "Cutover" in [docs/license-server-deployment.md](docs/license-server-deployment.md)):

- Point `jukeanator.com` and `www.jukeanator.com` at the server, and add the `MX` record (Step 11).
- Get an HTTPS certificate, for example with `apt -y install certbot python3-certbot-nginx` and `certbot --nginx -d jukeanator.com -d www.jukeanator.com`. When certbot asks whether to redirect HTTP to HTTPS, answer no; legacy clients need plain HTTP (Step 10).
- Set `email.verification-base-url` to `https://www.jukeanator.com`.
- Switch Braintree to the production keys and `braintree.environment: production`.
- Turn on geo-fencing (`app.geo-fence.enabled: true`).
- Change each kiosk's `app.master-instance-url` to `https://www.jukeanator.com`.
