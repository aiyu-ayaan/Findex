#!/usr/bin/env node
// Writes the release version into version.properties, which app/build.gradle.kts reads.
//   node scripts/set-version.mjs 0.0.1-alpha.1 [--bump-build]
// --bump-build also raises `build`: Android's versionCode.
import { readFileSync, writeFileSync } from 'node:fs'

const [version, flag] = process.argv.slice(2)
if (!/^\d+\.\d+\.\d+(-(alpha|beta)\.\d+)?$/.test(version ?? '')) throw new Error(`Invalid version '${version}'.`)

const build = +/^build=(\d+)$/m.exec(readFileSync('version.properties', 'utf8'))[1] + (flag === '--bump-build' ? 1 : 0)
writeFileSync('version.properties', `version=${version}\nbuild=${build}\n`)
console.log(`version ${version}, build ${build}`)
