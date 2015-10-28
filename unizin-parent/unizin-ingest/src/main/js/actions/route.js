import { pushState } from 'redux-router'
import { encodeURL } from './utils.js'

export function route(route, query) {
    return {
        ...pushState({}, route, query),
        // Views can call an action and pull the route key without dispatching
        // the action.
        route,
        query,
    }
}

export const RESET = 'RESET'

export function reset() {
    return { type: RESET }
}

export function routeSuccess(uid) {
    return route(encodeURL`/success/${uid}`)
}
