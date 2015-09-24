import styles from './search_result.scss'
import React from 'react'
import { Link } from 'react-router'
import { routeResult, routeReturnUrl } from '../actions/route.js'
import Cover from './cover.jsx'
import Pager from '../components/pager.jsx'

export default class SearchResults extends React.Component {
    static displayName = 'SearchResults'

    constructor(props, context) {
        super(props, context)

        this.onPage = this.onPage.bind(this)
        this.renderResult = this.renderResult.bind(this)
    }

    renderResult(result, index) {
        const { title } = result
        const returnUrl = routeReturnUrl(result).url
        const resultRoute = routeResult(result).route


        return (
            <li key={result.uid} className={styles.result}>

                <Cover className={styles.cover} document={result} />

                <h2>
                    <Link to={resultRoute}>
                        {title}
                    </Link>
                </h2>

                <a href={returnUrl} className={styles.btn}>
                    + Insert
                </a>
            </li>
        )
    }

    onPage(page) {
        const { criteria, searchFor } = this.props
        searchFor(criteria.text, page)
    }

    render() {
        const { criteria, results } = this.props
        if (!criteria.text) {
            return null
        }

        return (
            <main className={styles.results}>
                <h1>{results.totalSize} Results</h1>

                <Pager
                    current={this.props.page}
                    max={50}
                    onChange={this.onPage} />

                <ul>
                    {results.entries.map(this.renderResult)}
                </ul>

                <Pager
                    current={this.props.page}
                    max={50}
                    onChange={this.onPage} />
                </main>
        )
    }
}