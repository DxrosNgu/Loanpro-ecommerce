import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

client.interceptors.response.use(
  res => res,
  err => {
    const msg = err.response?.data?.message || err.message || 'Something went wrong'
    return Promise.reject(new Error(msg))
  }
)

export const productsApi = {
  list:   (page = 0, size = 12, sort = 'name') =>
    client.get('/products', { params: { page, size, sort } }).then(r => r.data),
  get:    (id)     => client.get(`/products/${id}`).then(r => r.data),
  create: (data)   => client.post('/products', data).then(r => r.data),
  update: (id, data) => client.put(`/products/${id}`, data).then(r => r.data),
  delete: (id)     => client.delete(`/products/${id}`),
  import: (file)   => {
    const fd = new FormData()
    fd.append('file', file)
    return client.post('/products/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }).then(r => r.data)
  },
}

export const searchApi = {
  search: (params) => client.get('/search', { params }).then(r => r.data),
}

export const ordersApi = {
  place:  (data) => client.post('/orders', data).then(r => r.data),
  get:    (id)   => client.get(`/orders/${id}`).then(r => r.data),
}

export default client
