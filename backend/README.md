Deployment Notes

Ensure Render root folder points to the backend directory (where package.json lives).

Recommended build command on Render:

pnpm install --include=dev && pnpm run build

Start command:

node dist/index.js

Set all environment variables in Render dashboard (service_role key, anon key, webhook secret, CORS, rate limits, port).
