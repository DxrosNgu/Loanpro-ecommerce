import { Link, useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useCart } from '../context/CartContext'

export default function Navbar() {
  const [searchParams] = useSearchParams()
  const [search, setSearch] = useState(searchParams.get('q') || '')
  const navigate = useNavigate()
  const location = useLocation()
  const { itemCount } = useCart()

  // Keep the search box in sync if the URL's q param changes elsewhere
  // (e.g. "Clear filters" on the product list page)
  useEffect(() => {
    setSearch(searchParams.get('q') || '')
  }, [searchParams])

  function handleSearch(e) {
    e.preventDefault()
    const trimmed = search.trim()
    const params = location.pathname === '/' ? new URLSearchParams(searchParams) : new URLSearchParams()
    if (trimmed) params.set('q', trimmed)
    else params.delete('q')
    navigate(`/?${params.toString()}`)
  }

  function handleLogoClick(e) {
    e.preventDefault()
    setSearch('')
    navigate('/')
  }

  return (
    <nav className="bg-white border-b border-gray-100 sticky top-0 z-30">
      <div className="max-w-6xl mx-auto px-4 h-14 flex items-center gap-4">
        <a
          href="/"
          onClick={handleLogoClick}
          className="font-semibold text-brand-700 text-lg flex items-center gap-2 shrink-0 cursor-pointer"
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z"/>
            <line x1="3" y1="6" x2="21" y2="6"/>
            <path d="M16 10a4 4 0 01-8 0"/>
          </svg>
          ShopLoan
        </a>

        <form onSubmit={handleSearch} className="flex-1 max-w-md">
          <input
            type="search"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search products…"
            className="input"
          />
        </form>

        <div className="flex items-center gap-2 shrink-0">
          <Link to="/products/import" className="btn-secondary text-sm hidden sm:flex">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
            Import CSV
          </Link>
          <Link to="/products/new" className="btn-primary text-sm">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            New product
          </Link>
          <Link to="/cart" className="btn-secondary text-sm relative" aria-label="View cart">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/>
              <path d="M1 1h4l2.68 13.39a2 2 0 002 1.61h9.72a2 2 0 002-1.61L23 6H6"/>
            </svg>
            {itemCount > 0 && (
              <span className="absolute -top-1.5 -right-1.5 bg-brand-500 text-white text-[10px] font-semibold rounded-full min-w-[18px] h-[18px] px-1 flex items-center justify-center">
                {itemCount}
              </span>
            )}
          </Link>
        </div>
      </div>
    </nav>
  )
}
