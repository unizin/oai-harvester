import { httpGET, httpPOST, json, encodeURL } from './utils.js'
import DataLoader from 'dataloader'
import { selectDocument, isDocumentReady } from '../selectors.js'

export const DOCUMENT_LOAD_ERROR = 'DOCUMENT_LOAD_ERROR'
export const CLEAR_DOCUMENT_LOAD_ERROR = 'CLEAR_DOCUMENT_LOAD_ERROR'
export const DOCUMENT = 'DOCUMENT'

const documentLoader = new DataLoader(docIds => {
    const options = {
        headers: {
            'X-NXDocumentProperties': '*'
        }
    }

    const ids = docIds.map(id => `'${id}'`).join(', ')
    const query = `select * from Document where ecm:uuid in (${ids})`
    const url = encodeURL`/nuxeo/site/api/v1/query?query=${query}`

    return httpGET(url, options).then(json).then((results) => {
        const map = results.entries.reduce((memo, next) => {
            memo[next.uid] = next
            return memo
        }, {})

        return docIds.map((id) => map[id])
    })
})

export function ensureDocument(id) {
    return (dispatch, getState) => {
        const { documents } = getState()

        if (!documents[id]) {
            documentLoader.load(id).then((doc) => {
                dispatch({
                    type: DOCUMENT,
                    payload: doc
                })
            })
        }
    }
}

function poll({ action, interval, timeout }) {
    const RETRY = Symbol('Retry')
    return new Promise((resolve, reject) => {
        let done = false
        setTimeout(() => {
            done = true
            reject(new Error('Timeout'))
        }, timeout)

        function next() {
            action(RETRY).then((result) => {
                if (result !== RETRY) {
                    return resolve(result)
                }

                if (!done) {
                    setTimeout(function() {
                        next()
                    }, interval)
                }
            }).catch(err => reject(err))
        }

        next()
    })
}

const DOCUMENT_IMPORT_TIMEOUT = 3000
const DOCUMENT_IMPORT_INTERVAL = 1000

export function documentImport(id) {
    const selector = selectDocument(id)
    const isReady = isDocumentReady(id)

    return (dispatch, getState) => {
        dispatch({
            type: CLEAR_DOCUMENT_LOAD_ERROR,
            payload: { id }
        })

        if (isReady(getState())) {
            return
        }

        documentImport.nxDownloadContent(id)

        function action(retry) {
            return documentImport.refreshDocument(id).then((doc) => {
                dispatch({
                    type: DOCUMENT,
                    payload: doc
                })
                const document = selector(getState())
                if (isReady(getState())) {
                    return document
                }
                return retry
            })
        }

        // Nothing needs to happen here if the process is successful
        poll({
            timeout: DOCUMENT_IMPORT_TIMEOUT,
            interval: DOCUMENT_IMPORT_INTERVAL,
            action,
        }).catch((error) => {
            return dispatch({
                type: DOCUMENT_LOAD_ERROR,
                payload: {
                    id,
                    message: error.message
                }
            })
        })

    }
}
// These are just being attached so they can be mocked in tests.
Object.assign(documentImport, {
    nxDownloadContent(id) {
        const url = encodeURL`/nuxeo/site/api/v1/id/${id}/@op/UnizinCMP.RetrieveCopyFromSourceRepository`
        return httpPOST(url, { params: {} })
    },
    refreshDocument(id) {
        documentLoader.clear(id)
        return documentLoader.load(id)
    }
})
