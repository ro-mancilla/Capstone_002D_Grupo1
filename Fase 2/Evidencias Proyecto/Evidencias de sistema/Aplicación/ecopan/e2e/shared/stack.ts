/** Fault injection on the throwaway stack: stop and start its containers. Only ever its own project. */
import { execFileSync } from "node:child_process"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { API } from "./api"

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..")
const PROJECT = "ecopan-acc"

function compose(...args: string[]): string {
  return execFileSync("docker", ["compose", "-p", PROJECT, "-f", "docker-compose.yml", "-f", "scripts/acceptance/compose.yml", "-f", "scripts/e2e/compose.yml", ...args], { cwd: ROOT, encoding: "utf8" })
}

export type Service = "web" | "rabbitmq" | "worker" | "notifier" | "db"

/** Runs [query] in the throwaway database and returns psql's unaligned output. */
export function sql(query: string): string {
  return compose("exec", "-T", "db", "psql", "-U", "postgres", "-d", "ecopan", "-v", "ON_ERROR_STOP=1", "-X", "-q", "-t", "-A", "-c", query).trim()
}

export function restartServices(...services: Service[]) { compose("restart", ...services) }

export function stopService(service: Service) { compose("stop", service) }

export async function startService(service: Service, healthy = true) {
  compose("start", service)
  if (service === "web" && healthy) await waitForApi()
}

export async function waitForApi(timeoutMs = 120_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try { if ((await fetch(`${API}/health`)).ok) return } catch { /* still down */ }
    await new Promise((r) => setTimeout(r, 1000))
  }
  throw new Error("the backend did not come back")
}
