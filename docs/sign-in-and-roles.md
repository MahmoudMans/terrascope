# Sign-in and roles

Sign-in is off by default, and everyone who can reach the app can do everything. Turn it on when more than one person uses the same installation.

## Turning it on

The quickest way is `bash scripts/start.sh --sign-in`. To do it by hand, put these in `.env`:

```
TERRASCOPE_SECURITY_ENABLED=true
TERRASCOPE_SECURITY_TOKEN_SECRET=<at least 32 characters>
TERRASCOPE_SECURITY_ADMIN_USERNAME=admin
TERRASCOPE_SECURITY_ADMIN_PASSWORD=<at least 12 characters>
```

`openssl rand -hex 32` makes a good secret. Then run `docker compose up -d`.

The backend refuses to start if sign-in is on and the secret is missing or shorter than 32 characters. The administrator variables only matter on the very first start, while no users exist. After that, manage accounts from the app. Turning sign-in off again keeps the accounts, and they work again when you turn it back on.

Changing the secret signs everyone out.

## Roles

| Role          | Can do                                                                                                                                              |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Viewer        | See all data, except the activity log and the user list. Run simulations and regional analysis, evaluate alert rules, verify records, and change their own password. |
| Operator      | Everything a Viewer can, plus changing data: incidents, alerts, watchlists, resilience records and so on. Can open the activity log.               |
| Administrator | Everything an Operator can, plus managing users and the governance closure actions.                                                                 |

## Managing users

Administrators open the account menu in the top bar and choose **Manage users** to add people, change roles, disable accounts and reset passwords. Everyone can choose **Change password** from the same menu.

- Usernames are 3 to 64 characters: lowercase letters, digits, dots, dashes or underscores.
- Passwords are 12 to 128 characters and must differ from the username.
- Changing someone's role, disabling them or resetting their password signs them out.
- You can't change your own role or disable your own account.
- The last enabled administrator can't be demoted or disabled.
- Five failed sign-ins for one username within 15 minutes lock that username for 15 minutes. Sixty failures from one address lock the address for the same time.

## Using the API directly

Sign in to get a token:

```
curl -s -X POST http://localhost:8087/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"YOUR_PASSWORD"}'
```

Send the token with each request:

```
curl -s http://localhost:8087/api/v1/auth/me -H 'Authorization: Bearer YOUR_TOKEN'
```

Only sign-in, the sign-in configuration and `/api/v1/status` are open. `/actuator/health` is open too, so health checks keep working.
