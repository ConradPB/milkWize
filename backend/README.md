Deployment Notes

Ensure Render root folder points to the backend directory (where package.json lives).

Recommended build command on Render:

pnpm install --include=dev && pnpm run build

Start command:

node dist/index.js
