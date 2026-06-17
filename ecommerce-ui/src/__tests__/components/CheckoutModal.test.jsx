import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import CheckoutModal from '../../components/CheckoutModal'
import * as client from '../../api/client'

vi.mock('../../api/client', () => ({
  ordersApi: { place: vi.fn() }
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const real = await vi.importActual('react-router-dom')
  return { ...real, useNavigate: () => mockNavigate }
})

const product = { id: 1, name: 'Running Shoes', price: '89.99' }

const renderModal = (qty = 1) => {
  const onClose = vi.fn()
  render(
    <MemoryRouter>
      <CheckoutModal product={product} quantity={qty} onClose={onClose} />
    </MemoryRouter>
  )
  return { onClose }
}

const fillForm = async (cardNumber = '4111111111111111') => {
  await userEvent.type(screen.getByPlaceholderText(/1234 5678/i), cardNumber)
  await userEvent.type(screen.getByPlaceholderText(/MM \/ YY/i), '12/26')
  await userEvent.type(screen.getByPlaceholderText(/123/i), '123')
  await userEvent.type(screen.getByPlaceholderText(/Jane Smith/i), 'John Doe')
}

describe('CheckoutModal', () => {

  beforeEach(() => vi.clearAllMocks())

  describe('rendering', () => {
    it('shows product name and price', () => {
      renderModal()
      expect(screen.getByText('Running Shoes × 1')).toBeInTheDocument()
      expect(screen.getByText('$89.99')).toBeInTheDocument()
    })

    it('shows total matching price × quantity', () => {
      renderModal(2)
      expect(screen.getByText('$179.98')).toBeInTheDocument()
    })

    it('renders all payment form fields', () => {
      renderModal()
      expect(screen.getByPlaceholderText(/1234 5678/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText(/MM \/ YY/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText(/123/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText(/Jane Smith/i)).toBeInTheDocument()
    })

    it('shows the declined-card tip', () => {
      renderModal()
      expect(screen.getByText(/0000.*declined/i)).toBeInTheDocument()
    })
  })

  describe('successful checkout', () => {
    it('calls ordersApi.place with correct payload', async () => {
      client.ordersApi.place.mockResolvedValue({ id: 42 })
      renderModal(2)
      await fillForm('4111111111111111')
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(client.ordersApi.place).toHaveBeenCalledWith({
          items: [{ productId: 1, quantity: 2 }],
          cardNumber: '4111111111111111',
        })
      })
    })

    it('strips spaces from card number before sending', async () => {
      client.ordersApi.place.mockResolvedValue({ id: 42 })
      renderModal()
      await fillForm('4111 1111 1111 1111')
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(client.ordersApi.place).toHaveBeenCalledWith(
          expect.objectContaining({ cardNumber: '4111111111111111' })
        )
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
    it('displays error message when API call fails', async () => {
      client.ordersApi.place.mockRejectedValue(new Error('Card declined by issuer'))
      renderModal()
      await fillForm()
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => {
        expect(screen.getByText('Card declined by issuer')).toBeInTheDocument()
      })
    })

    it('does NOT navigate on API failure', async () => {
      client.ordersApi.place.mockRejectedValue(new Error('Declined'))
      renderModal()
      await fillForm()
      await userEvent.click(screen.getByRole('button', { name: /pay/i }))
      await waitFor(() => expect(mockNavigate).not.toHaveBeenCalled())
    })
  })

  describe('close behaviour', () => {
    it('calls onClose when × button is clicked', async () => {
      const { onClose } = renderModal()
      await userEvent.click(screen.getByRole('button', { name: /close/i }))
      expect(onClose).toHaveBeenCalled()
    })
  })
})
