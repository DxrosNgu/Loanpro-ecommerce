import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useState, useEffect } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import CheckoutModal from '../../components/CheckoutModal'
import { CartProvider, useCart } from '../../context/CartContext'
import * as client from '../../api/client'

vi.mock('../../api/client', () => ({
  ordersApi: { place: vi.fn() }
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const real = await vi.importActual('react-router-dom')
  return { ...real, useNavigate: () => mockNavigate }
})

// CheckoutModal reads its line items from CartContext rather than props,
// so tests seed the cart through a small harness before rendering the modal.
function CheckoutWithSeededCart({ items, onClose = vi.fn() }) {
  const { addItem } = useCart()
  const [ready, setReady] = useState(false)

  useEffect(() => {
    items.forEach(({ product, quantity }) => addItem(product, quantity))
    setReady(true)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (!ready) return null
  return <CheckoutModal onClose={onClose} />
}

const shoe = { id: 1, name: 'Running Shoes', price: '89.99', stock: 150 }
const mouse = { id: 2, name: 'Wireless Mouse', price: '29.99', stock: 75 }

const renderModal = (items = [{ product: shoe, quantity: 1 }]) => {
  const onClose = vi.fn()
  render(
    <CartProvider>
      <MemoryRouter>
        <CheckoutWithSeededCart items={items} onClose={onClose} />
      </MemoryRouter>
    </CartProvider>
  )
  return { onClose }
}

async function fillForm({ card = '4111111111111111', expiry = '122030', cvv = '123', holder = 'John Doe' } = {}) {
  const cardInput = screen.getByPlaceholderText(/1234 5678/i)
  await userEvent.clear(cardInput)
  await userEvent.type(cardInput, card)

  const expiryInput = screen.getByPlaceholderText(/MM\/YYYY/i)
  await userEvent.clear(expiryInput)
  await userEvent.type(expiryInput, expiry)

  const cvvInput = screen.getByPlaceholderText('123')
  await userEvent.clear(cvvInput)
  await userEvent.type(cvvInput, cvv)

  const holderInput = screen.getByPlaceholderText(/Jane Smith/i)
  await userEvent.clear(holderInput)
  await userEvent.type(holderInput, holder)
}

describe('CheckoutModal', () => {

  beforeEach(() => vi.clearAllMocks())

  describe('rendering', () => {
    it('shows every item currently in the cart', () => {
      renderModal([
        { product: shoe, quantity: 2 },
        { product: mouse, quantity: 1 },
      ])
      expect(screen.getByText('Running Shoes × 2')).toBeInTheDocument()
      expect(screen.getByText('Wireless Mouse × 1')).toBeInTheDocument()
    })

    it('shows total summed across all cart items', () => {
      renderModal([
        { product: shoe, quantity: 2 },   // 179.98
        { product: mouse, quantity: 1 },  // 29.99
      ])
      expect(screen.getByText('$209.97')).toBeInTheDocument()
    })

    it('disables the Pay button when the cart is empty', () => {
      renderModal([])
      expect(screen.getByRole('button', { name: /pay/i })).toBeDisabled()
    })
  })

  describe('expiry validation (MM/YYYY)', () => {
    it('rejects an expired card', async () => {
      renderModal()
      await fillForm({ expiry: '012020' }) // formats to 01/2020
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(screen.getByText(/expired/i)).toBeInTheDocument()
      })
      expect(client.ordersApi.place).not.toHaveBeenCalled()
    })

    it('rejects an invalid month like 13', async () => {
      renderModal()
      await fillForm({ expiry: '132030' }) // formats to 13/2030
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(screen.getByText(/month must be between/i)).toBeInTheDocument()
      })
      expect(client.ordersApi.place).not.toHaveBeenCalled()
    })

    it('rejects an incomplete expiry (missing year digits)', async () => {
      renderModal()
      const expiryInput = screen.getByPlaceholderText(/MM\/YYYY/i)
      await userEvent.type(expiryInput, '1230') // only 2 year digits → 12/30
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(screen.getByText(/MM\/YYYY format/i)).toBeInTheDocument()
      })
      expect(client.ordersApi.place).not.toHaveBeenCalled()
    })

    it('auto-inserts the slash after two digits as the user types', async () => {
      renderModal()
      const expiryInput = screen.getByPlaceholderText(/MM\/YYYY/i)
      await userEvent.type(expiryInput, '122030')
      expect(expiryInput).toHaveValue('12/2030')
    })

    it('accepts a valid future MM/YYYY expiry', async () => {
      client.ordersApi.place.mockResolvedValue({ id: 1 })
      renderModal()
      await fillForm({ expiry: '122030' })
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(client.ordersApi.place).toHaveBeenCalled()
      })
    })
  })

  describe('CVV validation (exactly 3 digits)', () => {
    it('rejects a CVV shorter than 3 digits', async () => {
      renderModal()
      await fillForm({ cvv: '12' })
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(screen.getByText(/cvv must be exactly 3 digits/i)).toBeInTheDocument()
      })
      expect(client.ordersApi.place).not.toHaveBeenCalled()
    })

    it('strips non-numeric characters and caps input at 3 digits', async () => {
      renderModal()
      const cvvInput = screen.getByPlaceholderText('123')
      await userEvent.type(cvvInput, 'a1b2c3d4')
      expect(cvvInput).toHaveValue('123')
    })

    it('accepts a valid 3-digit CVV', async () => {
      client.ordersApi.place.mockResolvedValue({ id: 1 })
      renderModal()
      await fillForm({ cvv: '456' })
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(client.ordersApi.place).toHaveBeenCalled()
      })
    })
  })

  describe('other field validation', () => {
    it('rejects a card number shorter than 16 digits', async () => {
      renderModal()
      await fillForm({ card: '4111111111' }) // 10 digits
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(screen.getByText(/exactly 16 digits/i)).toBeInTheDocument()
      })
      expect(client.ordersApi.place).not.toHaveBeenCalled()
    })

    it('rejects a card number longer than 16 digits', async () => {
      renderModal()
      const cardInput = screen.getByPlaceholderText(/1234 5678/i)
      // formatCard caps raw input at 16 digits, so type extra digits via fireEvent-free
      // approach: type 16 valid digits, then attempt one more — input should not grow.
      await userEvent.type(cardInput, '41111111111111119999')
      expect(cardInput).toHaveValue('4111 1111 1111 1111') // capped at 16 digits client-side
    })

    it('accepts exactly 16 digits', async () => {
      client.ordersApi.place.mockResolvedValue({ id: 1 })
      renderModal()
      await fillForm({ card: '4111111111111111' }) // exactly 16 digits
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(client.ordersApi.place).toHaveBeenCalled()
      })
    })

    it('rejects a blank name on card', async () => {
      renderModal()
      await fillForm({ holder: '' })
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(screen.getByText(/name on card is required/i)).toBeInTheDocument()
      })
      expect(client.ordersApi.place).not.toHaveBeenCalled()
    })
  })

  describe('successful checkout', () => {
    it('sends every cart line item to ordersApi.place', async () => {
      client.ordersApi.place.mockResolvedValue({ id: 42 })
      renderModal([
        { product: shoe, quantity: 2 },
        { product: mouse, quantity: 3 },
      ])
      await fillForm()
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))

      await waitFor(() => {
        expect(client.ordersApi.place).toHaveBeenCalledWith({
          items: [
            { productId: 1, quantity: 2 },
            { productId: 2, quantity: 3 },
          ],
          cardNumber: '4111111111111111',
        })
      })
    })

    it('navigates to the order confirmation page on success', async () => {
      client.ordersApi.place.mockResolvedValue({ id: 42 })
      const { onClose } = renderModal()
      await fillForm()
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(onClose).toHaveBeenCalled()
        expect(mockNavigate).toHaveBeenCalledWith('/orders/42')
      })
    })
  })

  describe('failed checkout', () => {
    it('displays the error message returned by the API', async () => {
      client.ordersApi.place.mockRejectedValue(new Error('Card declined by issuer'))
      renderModal()
      await fillForm()
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(screen.getByText('Card declined by issuer')).toBeInTheDocument()
      })
    })

    it('does not navigate away when the API call fails', async () => {
      client.ordersApi.place.mockRejectedValue(new Error('Declined'))
      renderModal()
      await fillForm()
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => expect(mockNavigate).not.toHaveBeenCalled())
    })
  })

  describe('close behaviour', () => {
    it('calls onClose when the × button is clicked', async () => {
      const { onClose } = renderModal()
      await userEvent.click(screen.getByRole('button', { name: /close/i }))
      expect(onClose).toHaveBeenCalled()
    })
  })
})
