# Master Mode and Database Configuration

This guide covers the MySQL database (optional for standalone and slave installs, always used in master mode) and the setup of a master. The general PC setup (Java, media player, launch script, kiosk settings) is in [WindowsConfig.md](WindowsConfig.md) and [LinuxConfig.md](LinuxConfig.md). For building from source and running the tests, see [README.md](README.md).

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
