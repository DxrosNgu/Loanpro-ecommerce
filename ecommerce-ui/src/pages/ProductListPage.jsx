import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { productsApi, searchApi } from '../api/client'
import ProductCard from '../components/ProductCard'

const CATEGORIES = [
  'FOOTWEAR','ELECTRONICS','ACCESSORIES','FOOD_AND_BEVERAGE','SPORTS',
  'OUTDOORS','HOME_AND_OFFICE','CLOTHING','KITCHEN','BOOKS','GAMES',
  'BEAUTY','STATIONERY','HEALTH','PETS','TOOLS','GIFTS','MISC',
]

export default function ProductListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [page, setPage] = useState(0)

  // The URL is the single source of truth for filters, so the navbar search,
  // category dropdown, and price inputs never fight over state independently.
  const q        = searchParams.get('q')        || ''
  const category = searchParams.get('category')  || ''
  const minPrice = searchParams.get('minPrice')  || ''
  const maxPrice = searchParams.get('maxPrice')  || ''

  const isFiltering = Boolean(q || category || minPrice || maxPrice)

  // Reset to page 0 whenever the active filters change
  useEffect(() => { setPage(0) }, [q, category, minPrice, maxPrice])

  const { data, isLoading, isError } = useQuery({
    queryKey: ['products', q, category, minPrice, maxPrice, page],
    queryFn: () => isFiltering
      ? searchApi.search({
          q: q || undefined,
          category: category || undefined,
          minPrice: minPrice || undefined,
          maxPrice: maxPrice || undefined,
          page, size: 12,
        })
      : productsApi.list(page, 12),
  })

  function updateFilter(key, value) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    setSearchParams(next)
  }

  function clearFilters() {
    setSearchParams({})
  }

  return (
    <>
      <div className="mb-6 flex flex-wrap gap-3 items-end">
        <div className="flex-1 min-w-48">
          <label className="label">Category</label>
          <select
            value={category}
            onChange={e => updateFilter('category', e.target.value)}
            className="input"
          >
            <option value="">All categories</option>
            {CATEGORIES.map(c => (
              <option key={c} value={c}>{c.replace(/_/g,' ').toLowerCase()}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="label">Min price</label>
          <input type="number" min="0" step="0.01" value={minPrice}
            onChange={e => updateFilter('minPrice', e.target.value)}
            placeholder="$0" className="input w-28" />
        </div>
        <div>
          <label className="label">Max price</label>
          <input type="number" min="0" step="0.01" value={maxPrice}
            onChange={e => updateFilter('maxPrice', e.target.value)}
            placeholder="Any" className="input w-28" />
        </div>
        {isFiltering && (
          <button onClick={clearFilters} className="btn-ghost text-sm self-end mb-0.5">
            Clear filters
          </button>
        )}
      </div>

      {q && (
        <p className="text-sm text-gray-500 mb-4">
          Results for <span className="font-medium text-gray-900">"{q}"</span>
          {data && ` — ${data.totalElements} product${data.totalElements !== 1 ? 's' : ''}`}
        </p>
      )}

      {isLoading && (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {Array.from({length: 8}).map((_,i) => (
            <div key={i} className="card h-56 animate-pulse bg-gray-50" />
          ))}
        </div>
      )}

      {isError && (
        <div className="text-center py-16 text-red-500">
          Failed to load products. Is the API running?
        </div>
      )}

      {!isLoading && !isError && data?.content?.length === 0 && (
        <div className="text-center py-16 text-gray-400">
          <p className="text-lg font-medium mb-1">No products found</p>
          <p className="text-sm">Try adjusting your search or filters</p>
        </div>
      )}

      {data?.content?.length > 0 && (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {data.content.map(p => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>

          <div className="flex items-center justify-between mt-8">
            <p className="text-sm text-gray-500">
              {data.totalElements} products · page {data.page + 1} of {data.totalPages}
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => setPage(p => p - 1)}
                disabled={page === 0}
                className="btn-secondary text-sm"
              >
                Previous
              </button>
              <button
                onClick={() => setPage(p => p + 1)}
                disabled={data.last}
                className="btn-secondary text-sm"
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}
    </>
  )
}
