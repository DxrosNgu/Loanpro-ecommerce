import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ProductFormPage from '../../pages/ProductFormPage'
import { CartProvider, useCart } from '../../context/CartContext'
import * as client from '../../api/client'

vi.mock('../../api/client', () => ({
  productsApi: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  }
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const real = await vi.importActual('react-router-dom')
  return { ...real, useNavigate: () => mockNavigate }
})

const queryClient = () =>
  new QueryClient({ defaultOptions: { queries: { retry: false } } })

// Reads the live cart state out for assertions without re-mocking useCart,
// so the test exercises the real CartContext + ProductFormPage integration.
function CartPeek() {
  const { items } = useCart()
  const backpack = items.find(i => i.product.id === 1)
  return backpack ? (
    <p data-testid="cart-price-peek">{Number(backpack.product.price).toFixed(2)}</p>
  ) : null
}

const renderCreate = () =>
  render(
    <QueryClientProvider client={queryClient()}>
      <CartProvider>
        <MemoryRouter initialEntries={['/products/new']}>
          <Routes>
            <Route path="/products/new" element={<ProductFormPage />} />
          </Routes>
        </MemoryRouter>
      </CartProvider>
    </QueryClientProvider>
  )

const renderEdit = (id = '1') => {
  client.productsApi.get.mockResolvedValue({
    id: 1, name: 'Running Shoes', sku: 'RS-001',
    price: 89.99, stock: 150, category: 'FOOTWEAR',
    description: 'Light shoe', weightKg: 0.35
  })
  return render(
    <QueryClientProvider client={queryClient()}>
      <CartProvider>
        <MemoryRouter initialEntries={[`/products/${id}/edit`]}>
          <Routes>
            <Route path="/products/:id/edit" element={<ProductFormPage />} />
          </Routes>
        </MemoryRouter>
      </CartProvider>
    </QueryClientProvider>
  )
}

// Same as renderEdit, but seeds the cart with the product first and mounts
// CartPeek so the test can observe the price the cart is actually using.
function EditWithSeededCart({ id, initialPrice }) {
  const { addItem } = useCart()
  if (!EditWithSeededCart._seeded) {
    addItem({ id: Number(id), name: 'Running Shoes', price: initialPrice, stock: 150 }, 1)
    EditWithSeededCart._seeded = true
  }
  return (
    <>
      <CartPeek />
      <ProductFormPage />
    </>
  )
}

const renderEditWithSeededCart = (id = '1', initialPrice = 64.99) => {
  EditWithSeededCart._seeded = false
  client.productsApi.get.mockResolvedValue({
    id: 1, name: 'Backpack', sku: 'BP-001',
    price: initialPrice, stock: 10, category: 'ACCESSORIES',
    description: 'Travel backpack', weightKg: 0.8
  })
  return render(
    <QueryClientProvider client={queryClient()}>
      <CartProvider>
        <MemoryRouter initialEntries={[`/products/${id}/edit`]}>
          <Routes>
            <Route path="/products/:id/edit" element={<EditWithSeededCart id={id} initialPrice={initialPrice} />} />
          </Routes>
        </MemoryRouter>
      </CartProvider>
    </QueryClientProvider>
  )
}

describe('ProductFormPage', () => {

  beforeEach(() => vi.clearAllMocks())

  describe('create mode', () => {
    it('renders "New product" heading', () => {
      renderCreate()
      expect(screen.getByText('New product')).toBeInTheDocument()
    })

    it('shows all required form fields', () => {
      renderCreate()
      expect(screen.getByPlaceholderText('Running Shoes')).toBeInTheDocument()
      expect(screen.getByPlaceholderText('RS-001')).toBeInTheDocument()
      expect(screen.getByPlaceholderText('0.00')).toBeInTheDocument()
      expect(screen.getByPlaceholderText('0')).toBeInTheDocument()
    })

    it('shows validation error when name is missing', async () => {
      renderCreate()
      await userEvent.click(screen.getByRole('button', { name: /create product/i }))
      await waitFor(() => {
        expect(screen.getByText('Name is required')).toBeInTheDocument()
      })
    })

    it('shows validation error when SKU is missing', async () => {
      renderCreate()
      await userEvent.type(screen.getByPlaceholderText('Running Shoes'), 'My Shoe')
      await userEvent.click(screen.getByRole('button', { name: /create product/i }))
      await waitFor(() => {
        expect(screen.getByText('SKU is required')).toBeInTheDocument()
      })
    })

    it('calls productsApi.create with form values and navigates on success', async () => {
      client.productsApi.create.mockResolvedValue({
        id: 99, name: 'New Shoe', sku: 'NS-001', price: 49.99, stock: 10
      })
      renderCreate()

      await userEvent.type(screen.getByPlaceholderText('Running Shoes'), 'New Shoe')
      await userEvent.type(screen.getByPlaceholderText('RS-001'), 'NS-001')
      await userEvent.type(screen.getByPlaceholderText('0.00'), '49.99')
      await userEvent.type(screen.getByPlaceholderText('0'), '10')
      await userEvent.click(screen.getByRole('button', { name: /create product/i }))

      await waitFor(() => {
        expect(client.productsApi.create).toHaveBeenCalledWith(
          expect.objectContaining({ name: 'New Shoe', sku: 'NS-001' })
        )
        expect(mockNavigate).toHaveBeenCalledWith('/products/99')
      })
    })

    it('shows API error message when creation fails', async () => {
      client.productsApi.create.mockRejectedValue(new Error('SKU already exists'))
      renderCreate()

      await userEvent.type(screen.getByPlaceholderText('Running Shoes'), 'Shoe')
      await userEvent.type(screen.getByPlaceholderText('RS-001'), 'RS-001')
      await userEvent.type(screen.getByPlaceholderText('0.00'), '9.99')
      await userEvent.type(screen.getByPlaceholderText('0'), '5')
      await userEvent.click(screen.getByRole('button', { name: /create product/i }))

      await waitFor(() => {
        expect(screen.getByText('SKU already exists')).toBeInTheDocument()
      })
    })
  })

  describe('edit mode', () => {
    it('renders "Edit" heading', async () => {
      renderEdit('1')
      await waitFor(() => {
        expect(screen.getByText('Edit')).toBeInTheDocument()
      })
    })

    it('pre-fills all form fields from existing product', async () => {
      renderEdit('1')
      await waitFor(() => {
        expect(screen.getByDisplayValue('Running Shoes')).toBeInTheDocument()
        expect(screen.getByDisplayValue('RS-001')).toBeInTheDocument()
        expect(screen.getByDisplayValue('89.99')).toBeInTheDocument()
        expect(screen.getByDisplayValue('150')).toBeInTheDocument()
      })
    })

    it('calls productsApi.update on submit', async () => {
      client.productsApi.update.mockResolvedValue({ id: 1, name: 'Updated Shoe', sku: 'RS-001', price: 99.99, stock: 100 })
      renderEdit('1')

      await waitFor(() => screen.getByDisplayValue('Running Shoes'))
      const nameInput = screen.getByDisplayValue('Running Shoes')
      await userEvent.clear(nameInput)
      await userEvent.type(nameInput, 'Updated Shoe')
      await userEvent.click(screen.getByRole('button', { name: /save changes/i }))

      await waitFor(() => {
        expect(client.productsApi.update).toHaveBeenCalledWith('1',
          expect.objectContaining({ name: 'Updated Shoe' })
        )
      })
    })
  })

  describe('regression: cart showed stale price after editing a product already in the cart', () => {
    it('updates the price shown in the cart immediately after a successful edit', async () => {
      client.productsApi.update.mockResolvedValue({
        id: 1, name: 'Backpack', sku: 'BP-001', price: 34.99, stock: 10
      })
      renderEditWithSeededCart('1', 64.99)

      await waitFor(() => screen.getByDisplayValue('Backpack'))
      expect(screen.getByTestId('cart-price-peek')).toHaveTextContent('64.99')

      const priceInput = screen.getByDisplayValue('64.99')
      await userEvent.clear(priceInput)
      await userEvent.type(priceInput, '34.99')
      await userEvent.click(screen.getByRole('button', { name: /save changes/i }))

      await waitFor(() => {
        expect(screen.getByTestId('cart-price-peek')).toHaveTextContent('34.99')
      })
    })
  })
})
