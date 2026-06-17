import { useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { productsApi } from '../api/client'

const CATEGORIES = [
  'FOOTWEAR','ELECTRONICS','ACCESSORIES','FOOD_AND_BEVERAGE','SPORTS',
  'OUTDOORS','HOME_AND_OFFICE','CLOTHING','KITCHEN','BOOKS','GAMES',
  'BEAUTY','STATIONERY','HEALTH','PETS','TOOLS','GIFTS','MISC','UNCATEGORIZED',
]

export default function ProductFormPage() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const navigate = useNavigate()
  const qc = useQueryClient()

  const { data: existing, isLoading: loadingExisting } = useQuery({
    queryKey: ['product', id],
    queryFn: () => productsApi.get(id),
    enabled: isEdit,
  })

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm()

  useEffect(() => {
    if (existing) {
      reset({
        name:        existing.name,
        sku:         existing.sku,
        description: existing.description ?? '',
        category:    existing.category ?? '',
        price:       existing.price,
        stock:       existing.stock,
        weightKg:    existing.weightKg ?? '',
      })
    }
  }, [existing, reset])

  const mutation = useMutation({
    mutationFn: (data) => {
      const payload = {
        ...data,
        price:    Number(data.price),
        stock:    Number(data.stock),
        weightKg: data.weightKg ? Number(data.weightKg) : null,
        category: data.category || null,
      }
      return isEdit ? productsApi.update(id, payload) : productsApi.create(payload)
    },
    onSuccess: (product) => {
      qc.invalidateQueries({ queryKey: ['products'] })
      qc.invalidateQueries({ queryKey: ['product', String(product.id)] })
      navigate(`/products/${product.id}`)
    },
  })

  if (isEdit && loadingExisting) return <div className="animate-pulse h-96 bg-gray-50 rounded-xl" />

  return (
    <>
      <div className="mb-6 flex items-center gap-2 text-sm text-gray-500">
        <Link to="/" className="hover:text-brand-600">Products</Link>
        <span>/</span>
        {isEdit && existing && (
          <>
            <Link to={`/products/${id}`} className="hover:text-brand-600">{existing.name}</Link>
            <span>/</span>
          </>
        )}
        <span className="text-gray-900 font-medium">{isEdit ? 'Edit' : 'New product'}</span>
      </div>

      <div className="max-w-2xl">
        <form onSubmit={handleSubmit(d => mutation.mutateAsync(d))} className="card p-6 space-y-5">
          <div className="grid sm:grid-cols-2 gap-5">
            <div>
              <label className="label">Name *</label>
              <input
                className={`input ${errors.name ? 'border-red-300' : ''}`}
                placeholder="Running Shoes"
                {...register('name', { required: 'Name is required', maxLength: 255 })}
              />
              {errors.name && <p className="text-xs text-red-500 mt-1">{errors.name.message}</p>}
            </div>

            <div>
              <label className="label">SKU *</label>
              <input
                className={`input font-mono ${errors.sku ? 'border-red-300' : ''}`}
                placeholder="RS-001"
                {...register('sku', { required: 'SKU is required', maxLength: 100 })}
              />
              {errors.sku && <p className="text-xs text-red-500 mt-1">{errors.sku.message}</p>}
            </div>

            <div>
              <label className="label">Category</label>
              <select className="input" {...register('category')}>
                <option value="">— select —</option>
                {CATEGORIES.map(c => (
                  <option key={c} value={c}>{c.replace(/_/g,' ').toLowerCase()}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="label">Price *</label>
              <input
                type="number" step="0.01" min="0"
                className={`input ${errors.price ? 'border-red-300' : ''}`}
                placeholder="0.00"
                {...register('price', {
                  required: 'Price is required',
                  min: { value: 0, message: 'Price cannot be negative' }
                })}
              />
              {errors.price && <p className="text-xs text-red-500 mt-1">{errors.price.message}</p>}
            </div>

            <div>
              <label className="label">Stock *</label>
              <input
                type="number" min="0"
                className={`input ${errors.stock ? 'border-red-300' : ''}`}
                placeholder="0"
                {...register('stock', {
                  required: 'Stock is required',
                  min: { value: 0, message: 'Stock cannot be negative' }
                })}
              />
              {errors.stock && <p className="text-xs text-red-500 mt-1">{errors.stock.message}</p>}
            </div>

            <div>
              <label className="label">Weight (kg)</label>
              <input
                type="number" step="0.001" min="0"
                className="input"
                placeholder="0.000"
                {...register('weightKg', {
                  min: { value: 0, message: 'Weight cannot be negative' }
                })}
              />
            </div>
          </div>

          <div>
            <label className="label">Description</label>
            <textarea
              rows={3}
              className="input resize-none"
              placeholder="Describe the product…"
              {...register('description')}
            />
          </div>

          {mutation.isError && (
            <div className="bg-red-50 border border-red-100 text-red-700 text-sm rounded-lg p-3">
              {mutation.error?.message}
            </div>
          )}

          <div className="flex gap-2 justify-end pt-2">
            <Link to={isEdit ? `/products/${id}` : '/'} className="btn-secondary">
              Cancel
            </Link>
            <button type="submit" disabled={isSubmitting} className="btn-primary">
              {isSubmitting ? 'Saving…' : isEdit ? 'Save changes' : 'Create product'}
            </button>
          </div>
        </form>
      </div>
    </>
  )
}
