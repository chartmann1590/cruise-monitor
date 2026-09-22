# CruiseWatch Feedback Worker

Cloudflare Worker proxy between the Android app (untrusted client) and the GitHub REST API.

```
Android App --HTTPS/JSON--> Worker --Bearer GITHUB_TOKEN--> GitHub (issues, comments, feedback-assets/)
```

## Routes

- `GET /health`
- `POST /api/issues`
- `GET /api/issues/:number`
- `GET /api/issues/:number/comments`
- `POST /api/issues/:number/comments`
- `POST /api/assets`

The Worker owns repository routing via `GITHUB_REPO_OWNER` / `GITHUB_REPO_NAME` vars.
`GITHUB_TOKEN` is a Worker **secret** (never committed, never sent to clients).

## Setup

```bash
cd cloudflare/feedback-worker
npm install
npx wrangler login
npx wrangler secret put GITHUB_TOKEN
npx wrangler deploy
curl https://<worker-url>/health
```
