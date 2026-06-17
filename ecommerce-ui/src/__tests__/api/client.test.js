import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import axios from 'axios'
import { productsApi, searchApi, ordersApi } from '../../api/client'

vi.mock('axios', () => {
  const instance = {
    get:    vi.fn(),
    post:   vi.fn(),
    put:    vi.fn(),
    delete: vi.fn(),
    interceptors: {
      response: { use: vi.fn() }
    }
  }
  return { default: { create: () => instance, ...instance } }
})

const mockAxios = axios

describe('API client', () => {

  beforeEach(() => vi.clearAllMocks())

  describe('productsApi', () => {
    it('list() calls GET /products with page, size, sort', async () => {
      mockAxios.get.mockResolvedValue({ data: { content: [], totalElements: 0 } })
      await productsApi.list(0, 12, 'name')
      expect(mockAxios.get).toHaveBeenCalledWith('/products', {
        params: { page: 0, size: 12, sort: 'name' }
      })
    })

    it('get() calls GET /products/:id', async () => {
      mockAxios.get.mockResolvedValue({ data: { id: 1 } })
      await productsApi.get(1)
      expect(mockAxios.get).toHaveBeenCalledWith('/products/1')
    })

    it('create() calls POST /products with data', async () => {
      mockAxios.post.mockResolvedValue({ data: { id: 1 } })
      const payload = { name: 'Shoe', sku: 'SH-001', price: 9.99, stock: 5 }
      await productsApi.create(payload)
      expect(mockAxios.post).toHaveBeenCalledWith('/products', payload)
    })

    it('update() calls PUT /products/:id with data', async () => {
      mockAxios.put.mockResolvedValue({ data: { id: 1 } })
      const payload = { name: 'Updated Shoe', sku: 'SH-001', price: 19.99, stock: 10 }
      await productsApi.update(1, payload)
      expect(mockAxios.put).toHaveBeenCalledWith('/products/1', payload)
    })

    it('delete() calls DELETE /products/:id', async () => {
      mockAxios.delete.mockResolvedValue({})
      await productsApi.delete(1)
      expect(mockAxios.delete).toHaveBeenCalledWith('/products/1')
    })

    it('import() sends multipart/form-data with the file', async () => {
      mockAxios.post.mockResolvedValue({ data: { imported: 5 } })
      const file = new File(['name,sku\nShoe,SH-001'], 'test.csv', { type: 'text/csv' })
      await productsApi.import(file)
      expect(mockAxios.post).toHaveBeenCalledWith(
        '/products/import',
        expect.any(FormData),
        { headers: { 'Content-Type': 'multipart/form-data' } }
      )
    })
  })

  describe('searchApi', () => {
    it('search() calls GET /search with all provided params', async () => {
      mockAxios.get.mockResolvedValue({ data: { content: [] } })
      const params = { q: 'shoe', category: 'FOOTWEAR', minPrice: 10, maxPrice: 100 }
      await searchApi.search(params)
      expect(mockAxios.get).toHaveBeenCalledWith('/search', { params })
    })
  })

  describe('ordersApi', () => {
    it('place() calls POST /orders with the request body', async () => {
      mockAxios.post.mockResolvedValue({ data: { id: 42, status: 'PAID' } })
      const body = { items: [{ productId: 1, quantity: 2 }], cardNumber: '4111111111111111' }
      const result = await ordersApi.place(body)
      expect(mockAxios.post).toHaveBeenCalledWith('/orders', body)
      expect(result.status).toBe('PAID')
    })

    it('get() calls GET /orders/:id', async () => {
      mockAxios.get.mockResolvedValue({ data: { id: 42 } })
      await ordersApi.get(42)
      expect(mockAxios.get).toHaveBeenCalledWith('/orders/42')
    })
  })
})
