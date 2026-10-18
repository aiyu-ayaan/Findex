#!/usr/bin/env node
// Works out the next version from the commit subjects of a push to master.
// Ported from Domus; Findex builds one thing (the APK), so markers take no platform scope.
// A release is asked for by a marker at the START of a commit subject:
//
//   !alpha   0.0.0         -> 0.0.1-alpha.1  pre-release (0.0.0 = nothing released yet)
//            0.0.1-alpha.1 -> 0.0.1-alpha.2
//   !beta    0.0.1-alpha.3 -> 0.0.1-beta.1   promote, same base
//   !stable  0.0.1-beta.2  -> 0.0.1          promote to stable, no bump
//   !fix     1.4.2         -> 1.4.3          stable
//   !feat    1.4.2         -> 1.5.0          stable
//   !major   1.4.2         -> 2.0.0          stable
//   !patch   0.0.2         -> 0.0.2          no bump: rebuild that version in place
//
// When several pushed commits carry markers the strongest wins, in the order of MARKERS.
// Full rules: development/docs/RELEASE.md.
//
//   node scripts/release-version.mjs --current 0.0.0 --subject "!alpha : first"
//   node scripts/release-version.mjs --check      # self-check

import { strictEqual, throws } from 'node:assert'
import { readFileSync } from 'node:fs'

const CHANNELS = { alpha: 1, beta: 2 }
const MARKERS = ['major', 'feat', 'fix', 'stable', 'beta', 'alpha', 'patch']
const VERSION_RE = /^(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta)\.(\d+))?$/
const MARKER_RE = /^\s*!(major|feat|fix|stable|alpha|beta|patch)(?![a-z0-9])/i

export function parseVersion(version) {
  const m = VERSION_RE.exec(String(version).trim())
  if (!m) throw new Error(`Invalid version '${version}'. Expected X.Y.Z or X.Y.Z-alpha.N.`)
  return { major: +m[1], minor: +m[2], patch: +m[3], channel: m[4] ?? '', pre: m[5] ? +m[5] : 0 }
}

// The release flow's own commits (chore(release): vX) never start with `!`, so they are not markers.
export function detectMarker(subjects) {
  const found = new Set(subjects.map((s) => MARKER_RE.exec(s)?.[1].toLowerCase()))
  return MARKERS.find((marker) => found.has(marker)) ?? ''
}

export function nextVersion(current, marker) {
  const { major, minor, patch, channel, pre } = parseVersion(current)
  const base = `${major}.${minor}.${patch}`
  switch (marker) {
    case 'major': return `${major + 1}.0.0`
    case 'feat': return `${major}.${minor + 1}.0`
    case 'fix': return `${major}.${minor}.${patch + 1}`
    case 'stable':
      if (!channel) throw new Error(`!stable needs a pre-release to promote; ${current} is already stable.`)
      return base
    case 'patch': return String(current).trim()
    case 'alpha':
    case 'beta':
      // Same or lower channel: count up or promote on the same base. Otherwise
      // (from stable, or !alpha on a beta) start on the next patch.
      if (channel && CHANNELS[channel] <= CHANNELS[marker]) return `${base}-${marker}.${channel === marker ? pre + 1 : 1}`
      return `${major}.${minor}.${patch + 1}-${marker}.1`
    default: throw new Error(`Unknown marker '${marker}'.`)
  }
}

function selfCheck() {
  const eq = (current, marker, expected) => strictEqual(nextVersion(current, marker), expected, `${current} + !${marker}`)
  eq('0.0.0', 'alpha', '0.0.1-alpha.1')
  eq('0.0.0', 'beta', '0.0.1-beta.1')
  eq('0.0.0', 'fix', '0.0.1')
  eq('0.0.1-alpha.1', 'alpha', '0.0.1-alpha.2')
  eq('0.0.1-alpha.3', 'beta', '0.0.1-beta.1')
  eq('0.0.1-beta.1', 'beta', '0.0.1-beta.2')
  eq('0.0.1-beta.2', 'stable', '0.0.1')
  eq('0.0.1-beta.2', 'alpha', '0.0.2-alpha.1')
  eq('0.0.1', 'alpha', '0.0.2-alpha.1')
  eq('0.0.1', 'feat', '0.1.0')
  eq('0.1.0', 'major', '1.0.0')
  eq('0.0.2-beta.1', 'fix', '0.0.3')
  eq('0.0.2-alpha.3', 'patch', '0.0.2-alpha.3')
  throws(() => nextVersion('0.0.1', 'stable'), /already stable/)
  throws(() => parseVersion('1.2.3-rc.1'), /Invalid version/)

  strictEqual(detectMarker(['ui(Explorer) : nothing']), '')
  strictEqual(detectMarker(['!alpha : x', '!fix: y']), 'fix')
  strictEqual(detectMarker(['chore(release): v0.0.1']), '')
  strictEqual(detectMarker(['fix: mentions !feat in passing']), '')
  strictEqual(detectMarker(['!feature: not a marker']), '')
  strictEqual(detectMarker(['!alpha(Explorer) : x']), 'alpha')
  console.log('release-version self-check passed')
}

function main(argv) {
  if (argv.includes('--check')) return selfCheck()
  const arg = (name) => (argv.includes(name) ? (argv[argv.indexOf(name) + 1] ?? '') : '')
  const file = arg('--subjects-file')
  const subjects = (file ? readFileSync(file, 'utf8') : arg('--subject')).split('\n')
  const marker = detectMarker(subjects)
  if (!marker) return console.log('release=false')
  const version = nextVersion(arg('--current'), marker)
  const { channel } = parseVersion(version)
  console.log(
    [
      'release=true',
      `marker=${marker}`,
      `version=${version}`,
      `channel=${channel || 'latest'}`,
      `prerelease=${channel ? 'true' : 'false'}`,
    ].join('\n'),
  )
}

main(process.argv.slice(2))
