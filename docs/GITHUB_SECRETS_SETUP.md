# GitHub Secrets Setup — Do This Once

Go to: `github.com/ARGOD2213/ARGODGEIGN`  
`Settings -> Secrets and variables -> Actions -> New repository secret`

Add ALL of these secrets:

| Secret Name | Value |
|---|---|
| AWS_ACCESS_KEY_ID | Your AWS access key |
| AWS_SECRET_ACCESS_KEY | Your AWS secret key |
| EC2_INSTANCE_ID | From `setup-ec2.sh` output (example: `i-0abc123def`) |
| EC2_SSH_KEY | Contents of your EC2 `.pem` key file (full text) |
| WEATHER_API_KEY | Your OpenWeatherMap key |

## After secrets are set — mobile workflow

START demo:
- GitHub app -> Actions -> `📱 START IoT Server` -> Run workflow
- Wait ~3 minutes -> URLs appear in workflow summary
- Open URL on phone browser

STOP demo (important — saves money):
- GitHub app -> Actions -> `📱 STOP IoT Server` -> Run workflow
- EC2 stops -> zero compute charges

## Cost reminder
- Server costs roughly `~₹0.86/hour` (`t3.micro`, ap-south-1)
- Always run STOP workflow after demo
- `$140` budget lasts well over 1 year at this usage pattern
