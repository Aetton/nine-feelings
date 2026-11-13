// Publishes pre-signed public artifacts; no signing key is sent to GitHub.
const fs = require('node:fs');
const crypto = require('node:crypto');
const config = JSON.parse(fs.readFileSync('packaging/release-0.4.1.json', 'utf8'));
const repo = process.env.GITHUB_REPOSITORY;
if (repo !== 'Aetton/nine-feelings') throw new Error('Unexpected repository');
const token = process.env.GITHUB_TOKEN;
if (!token) throw new Error('Missing ephemeral workflow token');
const headers = { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28' };
async function api(path, options = {}) {
  const response = await fetch(`https://api.github.com/repos/${repo}${path}`, { ...options, headers: { ...headers, ...options.headers } });
  if (response.status === 404 && options.allow404) return null;
  const body = await response.json();
  if (!response.ok) throw new Error(`GitHub API ${response.status}: ${body.message || path}`);
  return body;
}
async function main() {
  const gradle = fs.readFileSync('app/build.gradle', 'utf8');
  if (!gradle.includes("versionName '0.4.1'") || !gradle.includes('versionCode 5')) throw new Error('Release/source version mismatch');
  const verified = [];
  for (const asset of config.assets) {
    const blob = await api(`/contents/${asset.name}?ref=${config.assetCommit}`);
    if (blob.encoding !== 'base64') throw new Error('Unexpected artifact encoding');
    const bytes = Buffer.from(blob.content, 'base64');
    if (crypto.createHash('sha256').update(bytes).digest('hex') !== asset.sha256) throw new Error(`Checksum mismatch: ${asset.name}`);
    verified.push({ ...asset, bytes });
  }
  const sums = verified.map(a => `${a.sha256}  ${a.name}`).join('\n') + '\n';
  verified.push({ name: 'SHA256SUMS.txt', bytes: Buffer.from(sums), contentType: 'text/plain' });
  let release = await api(`/releases/tags/${config.tag}`, { allow404: true });
  if (release && !release.draft) { console.log(`Already published: ${release.html_url}`); return; }
  if (!release) release = await api('/releases', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ tag_name: config.tag, target_commitish: process.env.GITHUB_SHA, name: 'Девять чувств / Nine Feelings 0.4.1', body: config.notes, draft: true, prerelease: true }) });
  if (!release.upload_url.startsWith(`https://uploads.github.com/repos/${repo}/releases/`)) throw new Error('Unexpected upload destination');
  for (const asset of verified) {
    const existing = release.assets.find(a => a.name === asset.name);
    if (existing) {
      const response = await fetch(`https://api.github.com/repos/${repo}/releases/assets/${existing.id}`, { headers: { ...headers, Accept: 'application/octet-stream' } });
      const bytes = Buffer.from(await response.arrayBuffer());
      if (!response.ok || !bytes.equals(asset.bytes)) throw new Error(`Existing asset differs: ${asset.name}`);
      continue;
    }
    const url = release.upload_url.replace(/\{.*$/, '') + '?name=' + encodeURIComponent(asset.name);
    const response = await fetch(url, { method: 'POST', headers: { ...headers, 'Content-Type': asset.contentType || 'application/octet-stream' }, body: asset.bytes });
    if (!response.ok) throw new Error(`Artifact upload failed: ${asset.name}, HTTP ${response.status}`);
  }
  release = await api(`/releases/${release.id}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ draft: false, prerelease: true }) });
  console.log(`Published release: ${release.html_url}`);
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
