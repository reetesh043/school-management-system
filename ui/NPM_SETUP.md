# Frontend dependency setup

The dependency versions in `package.json` are pinned exactly.

Run:

```bash
npm install
```

This will install the pinned direct dependencies and generate a complete `package-lock.json` with the resolved transitive dependencies and integrity hashes on your machine.

After that, commit `package-lock.json` and use:

```bash
npm ci
```

for reproducible installs in CI and on other machines.

Then start the UI with:

```bash
npm run dev
```
