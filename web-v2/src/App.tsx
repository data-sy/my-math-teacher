import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createBrowserRouter } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import Home from './screens/Home/Home'
import Entry from './screens/Entry/Entry'
import Quiz from './screens/Quiz/Quiz'
import Result from './screens/Result/Result'
import Login from './screens/Login/Login'
import GraphExplore from './screens/GraphExplore/GraphExplore'
import NotFound from './screens/NotFound/NotFound'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 60_000 },
  },
})

const router = createBrowserRouter([
  { path: '/', element: <Home /> },
  { path: '/entry', element: <Entry /> },
  { path: '/quiz', element: <Quiz /> },
  { path: '/result', element: <Result /> },
  { path: '/login', element: <Login /> },
  { path: '/graph', element: <GraphExplore /> },
  { path: '*', element: <NotFound /> },
])

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  )
}
