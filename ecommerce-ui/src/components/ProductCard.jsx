import { Link } from 'react-router-dom'
import { useState } from 'react'
import { useCart } from '../context/CartContext'

const CATEGORY_COLORS = {
  FOOTWEAR: 'badge-blue', ELECTRONICS: 'badge-blue', ACCESSORIES: 'badge-gray',
  SPORTS: 'badge-green', OUTDOORS: 'badge-green', HOME_AND_OFFICE: 'badge-gray',
  CLOTHING: 'badge-blue', KITCHEN: 'badge-amber', FOOD_AND_BEVERAGE: 'badge-amber',
  BEAUTY: 'badge-amber', BOOKS: 'badge-gray', GAMES: 'badge-blue',
}

export default function ProductCard({ product }) {
  const { addItem } = useCart()
  const [added, setAdded] = useState(false)
  const inStock = product.stock > 0
  const catClass = CATEGORY_COLORS[product.category] || 'badge-gray'
  const catLabel = product.category?.replace(/_/g, ' ').toLowerCase() ?? 'uncategorized'

  function handleAddToCart() {
    addItem(product, 1)
    setAdded(true)
    setTimeout(() => setAdded(false), 1200)
  }

  return (
    <div className="card flex flex-col overflow-hidden group hover:border-brand-100 transition-colors">
      <div className="bg-surface h-32 flex items-center justify-center text-4xl text-gray-300 font-mono">
        {product.name.charAt(0)}
      </div>

      <div className="p-3 flex flex-col flex-1">
        <div className="flex items-start justify-between gap-1 mb-0.5">
          <Link
            to={`/products/${product.id}`}
            className="text-sm font-medium text-gray-900 hover:text-brand-600 leading-tight line-clamp-2"
          >
            {product.name}
          </Link>
        </div>

        <p className="text-[10px] font-mono text-gray-400 mb-2">{product.sku}</p>

        <div className="flex items-center gap-1.5 mb-3 flex-wrap">
          <span className={catClass} style={{fontSize:'10px'}}>
            {catLabel}
          </span>
          <span className={inStock ? 'badge-green' : 'badge-red'} style={{fontSize:'10px'}}>
            {inStock ? `${product.stock} in stock` : 'out of stock'}
          </span>
        </div>

        <div className="mt-auto flex items-center justify-between">
          <span className="text-base font-semibold text-gray-900">
            ${Number(product.price).toFixed(2)}
          </span>
          <div className="flex gap-1">
            <Link to={`/products/${product.id}/edit`} className="btn-ghost px-2 py-1.5 text-xs">
              Edit
            </Link>
            <button
              onClick={handleAddToCart}
              disabled={!inStock}
              className="btn-primary px-3 py-1.5 text-xs min-w-[64px]"
            >
              {added ? 'Added ✓' : 'Add to cart'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
