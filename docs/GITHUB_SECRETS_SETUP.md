# GitHub Secrets Setup - Do This Once

Go to: `github.com/ARGOD2213/ARGODGEIGN`
`Settings -> Secrets and variables -> Actions -> New repository secret`

Add these required secrets:

| Secret Name | Value |
|---|---|
| AWS_ACCESS_KEY_ID | Your AWS access key |
| AWS_SECRET_ACCESS_KEY | Your AWS secret key |
| EC2_INSTANCE_ID | From `setup-ec2.sh` output, for example `i-0abc123def` |
| EC2_SSH_KEY | Full contents of your EC2 `.pem` key file |
| WEATHER_API_KEY | Your OpenWeatherMap key |

Optional dashboard login overrides:

| Secret Name | Value |
|---|---|
| ARGUS_DASHBOARD_USER | Custom Basic Auth username |
| ARGUS_DASHBOARD_PASS | Custom Basic Auth password |

If you do not set the optional dashboard secrets, the mobile workflow uses:
- Username: `argus`
- Password: `changeme`

## After secrets are set - mobile workflow

START demo:
- GitHub app -> Actions -> `START IoT Server` -> Run workflow
- Wait for workflow success -> URLs appear in the workflow summary
- The workflow now waits for EC2 status checks, local app health, and public app health before reporting success
- Open the dashboard URL on your phone browser

STOP demo:
- GitHub app -> Actions -> `STOP IoT Server` -> Run workflow
- EC2 stops -> zero compute charges

## Cost reminder

- Server cost is roughly `~INR 0.86/hour` for `t3.micro` in `ap-south-1`
- Always run the STOP workflow after the demo
- A `$140` budget lasts well over a year at this usage pattern
