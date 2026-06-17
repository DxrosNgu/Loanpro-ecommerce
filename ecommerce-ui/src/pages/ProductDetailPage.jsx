import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { productsApi } from '../api/client'
import CheckoutModal from '../components/CheckoutModal'

export default function ProductDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [qty, setQty] = useState(1)
  const [buying, setBuying] = useState(false)

  const { data: product, isLoading, isError } = useQuery({
    queryKey: ['product', id],
    queryFn: () => productsApi.get(id),
  })

  const deleteMutation = useMutation({
    mutationFn: () => productsApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] })
      navigate('/')
    },
  })

  if (isLoading) return <div className="animate-pulse h-96 bg-gray-50 rounded-xl" />
  if (isError) return <div className="text-red-500 text-center py-16">Product not found.</div>

  const inStock = product.stock > 0
  const catLabel = product.category?.replace(/_/g, ' ').toLowerCase() ?? 'uncategorized'

  return (
    <>
      <div className="mb-6 flex items-center gap-2 text-sm text-gray-500">
        <Link to="/" className="hover:text-brand-600">Products</Link>
        <span>/</span>
        <span className="text-gray-900 font-medium">{product.name}</span>
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        <div className="card h-64 md:h-auto flex items-center justify-center text-8xl text-gray-200 font-mono">
          {product.name.charAt(0)}
        </div>

        <div className="flex flex-col gap-4">
          <div>
            <p className="text-xs font-mono text-gray-400 mb-1">{product.sku}</p>
            <h1 className="text-2xl font-semibold text-gray-900 mb-2">{product.name}</h1>
            <div className="flex gap-2 flex-wrap mb-3">
              <span className="badge-blue text-[11px]">{catLabel}</span>
              <span className={`text-[11px] ${inStock ? 'badge-green' : 'badge-red'}`}>
                {inStock ? `${product.stock} in stock` : 'out of stock'}
              </span>
            </div>
            <p className="text-3xl font-bold text-gray-900">${Number(product.price).toFixed(2)}</p>
          </div>

          {product.description && (
            <p className="text-sm text-gray-600 leading-relaxed">{product.description}</p>
          )}

          <div className="card p-4 grid grid-cols-2 gap-3 text-sm">
            {product.weightKg && (
              <>
                <span className="text-gray-500">Weight</span>
                <span className="font-medium">{product.weightKg} kg</span>
              </>
            )}
            <span className="text-gray-500">SKU</span>
            <span className="font-mono text-xs">{product.sku}</span>
          </div>

          <div className="flex items-center gap-3">
            <span className="text-sm text-gray-500">Qty</span>
            <div className="flex items-center gap-2 border border-gray-200 rounded-lg px-2">
              <button
                onClick={() => setQty(q => Math.max(1, q - 1))}
                className="py-1.5 px-1 text-gray-500 hover:text-gray-900"
              >−</button>
              <span className="w-8 text-center font-medium text-sm">{qty}</span>
              <button
                onClick={() => setQty(q => Math.min(product.stock, q + 1))}
                disabled={!inStock}
                className="py-1.5 px-1 text-gray-500 hover:text-gray-900"
              >+</button>
            </div>
          </div>

          <div className="flex gap-2 flex-wrap">
            <button
              onClick={() => setBuying(true)}
              disabled={!inStock}
              className="btn-primary flex-1"
            >
              Buy now — ${(Number(product.price) * qty).toFixed(2)}
            </button>
            <Link to={`/products/${id}/edit`} className="btn-secondary">
              Edit
            </Link>
            <button
              onClick={() => {
                if (confirm(`Delete "${product.name}"? This cannot be undone.`))
                  deleteMutation.mutate()
              }}
              className="btn-danger"
            >
              Delete
            </button>
          </div>
        </div>
      </div>

      {buying && (
        <CheckoutModal product={product} quantity={qty} onClose={() => setBuying(false)} />
      )}
    </>
  )
}
