import { fileURLToPath, URL } from 'node:url';

import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// https://vitejs.dev/config/
export default defineConfig(() => {
    return {
        plugins: [vue()],
        resolve: {
            alias: {
                '@': fileURLToPath(new URL('./src', import.meta.url))
            }
        }
    };
});

// export default defineConfig({
//     plugins: [vue()],
//     resolve: {
//       alias: {
//         '@': fileURLToPath(new URL('./src', import.meta.url))
//       }
//     },
//     server: {
//       proxy: {
//         '/gogo': {
//           target: 'http://localhost:8080', 
//           changeOrigin: true,
//           rewrite: (path) => path.replace(/^\/gogo/, '/api/v1/hello/gogo'), // 실제 요청에서 '/backend-api' 부분을 제거합니다.
//         },
//       },
//     },
//   });
