import { resolve } from 'path'
import Vue from '@vitejs/plugin-vue'
import VueJsx from '@vitejs/plugin-vue-jsx'
import progress from 'vite-plugin-progress'
import EslintPlugin from 'vite-plugin-eslint'
import PurgeIcons from 'vite-plugin-purge-icons'
import { ViteEjsPlugin } from 'vite-plugin-ejs'
// @ts-ignore
import ElementPlus from 'unplugin-element-plus/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import viteCompression from 'vite-plugin-compression'
import topLevelAwait from 'vite-plugin-top-level-await'
import VueI18nPlugin from '@intlify/unplugin-vue-i18n/vite'
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons-ng'
import UnoCSS from 'unocss/vite'

// 菜单图标来自数据库，静态扫描无法自动发现。内部部署将当前启用菜单用到的
// 图标显式打包，避免浏览器运行时访问公共 Iconify API。
const INTERNAL_MENU_ICONS = [
  'ep:aim',
  'ep:avatar',
  'ep:bell',
  'ep:bicycle',
  'ep:calendar',
  'ep:chat-dot-round',
  'ep:checked',
  'ep:coffee-cup',
  'ep:collection',
  'ep:connection',
  'ep:data-analysis',
  'ep:data-line',
  'ep:dish-dot',
  'ep:document',
  'ep:document-checked',
  'ep:document-copy',
  'ep:files',
  'ep:grape',
  'ep:house',
  'ep:management',
  'ep:menu',
  'ep:message',
  'ep:message-box',
  'ep:money',
  'ep:monitor',
  'ep:notebook',
  'ep:odometer',
  'ep:operation',
  'ep:picture',
  'ep:pie-chart',
  'ep:place',
  'ep:position',
  'ep:promotion',
  'ep:set-up',
  'ep:takeaway-box',
  'ep:tools',
  'ep:upload-filled',
  'ep:user',
  'ep:warning',
  'ep:warning-filled',
  'fa-solid:box',
  'fa-solid:charging-station',
  'fa-solid:file-signature',
  'fa-solid:house-user',
  'fa-solid:mail-bulk',
  'fa-solid:swimming-pool',
  'fa-solid:tasks',
  'fa-solid:window-restore',
  'fa:address-book-o',
  'fa:address-card',
  'fa:archive',
  'fa:area-chart',
  'fa:bars',
  'fa:battery-3',
  'fa:bus',
  'fa:connectdevelop',
  'fa:dashcube',
  'fa:edit',
  'fa:eye',
  'fa:fighter-jet',
  'fa:hdd-o',
  'fa:key',
  'fa:map-marker',
  'fa:money',
  'fa:pagelines',
  'fa:power-off',
  'fa:product-hunt',
  'fa:reddit-square',
  'fa:rocket',
  'fa:stack-exchange',
  'fa:tag',
  'fa:tasks',
  'fa:tree',
  'fa:universal-access',
  'fa:wpforms',
  'simple-icons:civicrm'
]

export function createVitePlugins() {
  const root = process.cwd()

  // 路径查找
  function pathResolve(dir: string) {
    return resolve(root, '.', dir)
  }

  return [
    Vue(),
    VueJsx(),
    UnoCSS(),
    progress(),
    PurgeIcons({
      included: INTERNAL_MENU_ICONS,
      iconSource: 'local'
    }),
    ElementPlus({}),
    AutoImport({
      include: [
        /\.[tj]sx?$/, // .ts, .tsx, .js, .jsx
        /\.vue$/,
        /\.vue\?vue/, // .vue
        /\.md$/ // .md
      ],
      imports: [
        'vue',
        'vue-router',
        // 可额外添加需要 autoImport 的组件
        {
          '@/hooks/web/useI18n': ['useI18n'],
          '@/hooks/web/useMessage': ['useMessage'],
          '@/hooks/web/useTable': ['useTable'],
          '@/hooks/web/useCrudSchemas': ['useCrudSchemas'],
          '@/utils/formRules': ['required'],
          '@/utils/dict': ['DICT_TYPE']
        }
      ],
      dts: 'src/types/auto-imports.d.ts',
      resolvers: [ElementPlusResolver()],
      eslintrc: {
        enabled: false, // Default `false`
        filepath: './.eslintrc-auto-import.json', // Default `./.eslintrc-auto-import.json`
        globalsPropValue: true // Default `true`, (true | false | 'readonly' | 'readable' | 'writable' | 'writeable')
      }
    }),
    Components({
      // 生成自定义 `auto-components.d.ts` 全局声明
      dts: 'src/types/auto-components.d.ts',
      // 自定义组件的解析器
      resolvers: [ElementPlusResolver()],
      globs: ["src/components/**/**.{vue, md}", '!src/components/DiyEditor/components/mobile/**']
    }),
    EslintPlugin({
      cache: false,
      include: ['src/**/*.vue', 'src/**/*.ts', 'src/**/*.tsx'] // 检查的文件
    }),
    VueI18nPlugin({
      runtimeOnly: true,
      compositionOnly: true,
      include: [resolve(__dirname, 'src/locales/**')]
    }),
    createSvgIconsPlugin({
      iconDirs: [pathResolve('src/assets/svgs')],
      symbolId: 'icon-[dir]-[name]',
    }),
    viteCompression({
      verbose: true, // 是否在控制台输出压缩结果
      disable: false, // 是否禁用
      threshold: 10240, // 体积大于 threshold 才会被压缩,单位 b
      algorithm: 'gzip', // 压缩算法,可选 [ 'gzip' , 'brotliCompress' ,'deflate' , 'deflateRaw']
      ext: '.gz', // 生成的压缩包后缀
      deleteOriginFile: false //压缩后是否删除源文件
    }),
    ViteEjsPlugin(),
    topLevelAwait({
      // https://juejin.cn/post/7152191742513512485
      // The export name of top-level await promise for each chunk module
      promiseExportName: '__tla',
      // The function to generate import names of top-level await promise in each chunk module
      promiseImportName: (i) => `__tla_${i}`
    })
  ]
}
