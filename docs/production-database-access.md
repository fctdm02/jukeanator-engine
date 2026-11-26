# Production Server Access: SSH Keys and the Database in DBeaver

How to reach the production server (`www.jukeanator.com`, `65.98.125.113`) and its MySQL database from a development PC, including setting up a new PC from scratch, for example after the current one fails.

## How access works

- **SSH with a key, never a password.** The server accepts SSH logins only with a key; password logins are switched off (`/etc/ssh/sshd_config.d/00-hardening.conf`). The one key it accepts today is the ed25519 key named `jukeanator-prod-deploy`, kept on the development PC as `C:\Users\<you>\.ssh\jukeanator_prod` (private) and `jukeanator_prod.pub` (public).
- **The same key is used for everything:** `ssh jukeanator-prod`, copying the WAR for a deploy, and the DBeaver tunnel.
- **MySQL is not on the internet.** It listens only on the server itself (`bind-address = 127.0.0.1`), and the firewall allows only SSH (22), HTTP (80), HTTPS (443) and mail (25). DBeaver therefore reaches it through an SSH tunnel: it signs in over SSH, then connects to MySQL from the server's side. Keep it this way; never open port 3306.
- **The root password** is kept by the owner for the Mochahost web console only. It cannot be used over SSH.

## Back up the key now

The private key has no passphrase. Anyone holding the file can sign in to the server as root, and without the file this PC is the only way in except for the Mochahost web console.

- Keep a copy of `jukeanator_prod` and `jukeanator_prod.pub` somewhere safe and offline, for example an encrypted USB drive or a password manager that stores files (as a secure attachment).
- Never email it, put it in git, or store it in plain cloud storage.
- Optional: add a passphrase to the key on this PC with `ssh-keygen -p -f $HOME\.ssh\jukeanator_prod`. It is then asked for on each use (DBeaver can remember it).

---

## Part 1: SSH on a new PC

Windows 10 and 11 include the OpenSSH client (`ssh`, `scp`, `ssh-keygen`); check with `ssh -V` in PowerShell. Pick the case that applies.

### Case A: you have the backup of the key

Copy both files into your `.ssh` folder:

```powershell
New-Item -ItemType Directory -Force $HOME\.ssh
```

```powershell
Copy-Item E:\backup\jukeanator_prod, E:\backup\jukeanator_prod.pub $HOME\.ssh\
```

(Replace `E:\backup` with wherever the backup is.) Windows' SSH refuses a private key that other users can read, so limit it to your own account:

```powershell
icacls $HOME\.ssh\jukeanator_prod /inheritance:r /grant:r "$($env:USERNAME):F"
```

Then add the SSH settings below ("The SSH config entry") and test.

### Case B: a new key, while another PC can still sign in

Create a new key on the new PC (press Enter for no passphrase, or type one):

```powershell
ssh-keygen -t ed25519 -C "jukeanator-prod-<new-pc-name>" -f $HOME\.ssh\jukeanator_prod
```

Copy the new **public** key (`jukeanator_prod.pub`, one line starting `ssh-ed25519`) to the PC that can still sign in. The public key is not secret, so email or a USB stick is fine. On that PC, add it to the server's list of allowed keys:

```powershell
Get-Content .\jukeanator_prod.pub | ssh jukeanator-prod "cat >> /root/.ssh/authorized_keys"
```

Then add the SSH settings below on the new PC and test.

### Case C: a new key, and no PC can sign in any more

Create the new key as in Case B. Then use the Mochahost web console, which signs in with the root password, not a key:

- Sign in to the Mochahost client area, open the VPS ("Frappe Linux I", 65.98.125.113) and open its **Console** (VNC).
- Log in as `root` with the root password.
- Add the public key's single line to `/root/.ssh/authorized_keys`. Typing a 100-character key into the console is error-prone. Two easier ways:
  - If the console offers a clipboard or paste feature, run `nano /root/.ssh/authorized_keys`, paste the line at the end, then save with Ctrl+O, Enter, Ctrl+X.
  - Or publish the public key where the server can download it, for example add it to a GitHub account (Settings, SSH and GPG keys), then in the console run `curl -s https://github.com/<github-user>.keys >> /root/.ssh/authorized_keys`. This is safe because a public key is not secret.
- Check the file: `cat /root/.ssh/authorized_keys` must show one key per line.

Afterwards, remove the lost PC's key from that file (its line ends with its name, for example `jukeanator-prod-deploy`), so a found or stolen old disk can no longer sign in.

### The SSH config entry

Create or edit `C:\Users\<you>\.ssh\config` (a plain text file with no extension) and add:

```
Host jukeanator-prod
    HostName 65.98.125.113
    User root
    Port 22
    IdentityFile ~/.ssh/jukeanator_prod
    IdentitiesOnly yes
    ServerAliveInterval 30
```

### Test it

```powershell
ssh jukeanator-prod "hostname; systemctl is-active jukeanator"
```

The first time, SSH shows the server's key fingerprint and asks to trust it; answer `yes`. The command should print `app` and `active`.

---

## Part 2: a read-only database account (once per server)

DBeaver should use its own account that can only read. The application keeps all users in memory and writes its own copy back, so an edit made while it runs can be overwritten, or can leave the database and the application disagreeing. A read-only account rules that out. It only needs to be created once; a new PC reuses it.

Sign in to the server:

```powershell
ssh jukeanator-prod
```

Then type `mysql` (root signs in to MySQL without a password on the server itself) and run the following, with a password of your own choosing. Keep the password in your password manager.

```sql
CREATE USER 'dbeaver_ro'@'localhost' IDENTIFIED BY 'choose-a-strong-password';
GRANT SELECT ON jukeanator.* TO 'dbeaver_ro'@'localhost';
```

Type `exit` to leave MySQL and `exit` again to leave the server.

Notes:
- `localhost` is correct even though DBeaver is on another PC: through the tunnel, the connection arrives at MySQL from the server itself.
- To change the password later: `ALTER USER 'dbeaver_ro'@'localhost' IDENTIFIED BY 'new-password';`
- To check the account exists: `SELECT user, host FROM mysql.user;`

---

## Part 3: DBeaver

### Install

Download DBeaver Community from dbeaver.io and install it. On the first connection it offers to download the MySQL driver; accept.

### Create the connection

- Database, **New Database Connection**, choose **MySQL**, Next.
- **SSH** tab (in older versions, under the **SSH, SSL, ...** tab):
  - tick **Use SSH Tunnel**
  - Host/IP: `65.98.125.113`, Port: `22`
  - User Name: `root`
  - Authentication Method: **Public Key**
  - Private Key: `C:\Users\<you>\.ssh\jukeanator_prod`
  - Passphrase: only if the key has one
  - click **Test tunnel configuration**; it should succeed
- **Main** tab:
  - Server Host: `localhost` (MySQL on the server, reached through the tunnel)
  - Port: `3306`
  - Database: `jukeanator`
  - Username: `dbeaver_ro`, Password: the one from Part 2 (tick **Save password** if you want)
- **Driver properties** tab: set `allowPublicKeyRetrieval` to `true`. MySQL 8's default sign-in method needs it when the MySQL connection itself does not use SSL; the SSH tunnel already encrypts everything.
- Optional: on the **General** page, name it `JukeANator PROD`, set the connection type to **Production** (DBeaver colours it and asks before running changes), and tick **Read-only connection** as a second safeguard.
- **Test Connection**, then **Finish**.

### If it does not connect

- **Tunnel test fails:** check `ssh jukeanator-prod` works in PowerShell first. If it does, the key path in DBeaver is wrong, or the key has a passphrase that was not entered.
- **"Public Key Retrieval is not allowed":** `allowPublicKeyRetrieval` is not set to `true`.
- **"Access denied for user 'dbeaver_ro'@'localhost'":** wrong password, or the account was never created (Part 2).
- **"Unable to load authentication plugin 'caching_sha2_password'":** DBeaver's MySQL driver is old; edit the connection, Edit Driver Settings, Libraries, and download or update the driver.

---

## Changing production data

Do not change production data from DBeaver while the application runs. When a change is needed (for example removing test purchases), follow the same routine every time, signed in over SSH:

- Back up the rows first, for example `mysqldump --no-create-info jukeanator <table> --where="..." > /var/backups/jukeanator/<name>.sql`.
- Stop the application: `systemctl stop jukeanator`.
- Make the change in one transaction (`START TRANSACTION; ... COMMIT;`), with `WHERE` clauses that name the exact rows.
- Start the application: `systemctl start jukeanator`, then check `/var/log/jukeanator/jukeanator-service.log` for errors.

The nightly database backups are in `/var/backups/jukeanator` on the server (kept 30 days); there is no off-site copy.

## Related

- [MasterConfig.md](../MasterConfig.md): how the production server was set up, including the accounts step.
