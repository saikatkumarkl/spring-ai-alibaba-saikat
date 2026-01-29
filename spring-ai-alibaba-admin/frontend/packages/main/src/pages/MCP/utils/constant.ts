import $i18n from '@/i18n';
import { IRadioItemProps } from '../components/RadioItem';

export const installTypeOptions: IRadioItemProps[] = [
  {
    label: 'SSE',
    value: 'SSE',
    logo: 'spark-internet-line',
  },
];

export const MCP_TIP_SECTIONS = [
  {
    title: $i18n.get({
      id: 'main.pages.MCP.Create.mcpMarket',
      dm: 'MCP Marketplace',
    }),
    linkButtons: [
      { text: 'ModelScope MCP', url: 'https://modelscope.cn/mcp' },
      { text: 'MCP.so', url: 'https://mcp.so' },
      { text: 'Simthery', url: 'https://smithery.ai/' },
    ],
    description: $i18n.get({
      id: 'main.pages.MCP.Create.mcpMarketDescription',
      dm: 'Get services from mainstream MCP marketplaces like ModelScope MCP, MCP.so, Smithery, and configure the server address below to complete the registration of your custom MCP service!',
    }),
  },
  {
    title: 'Nacos',
    linkButtons: [
      {
        text: 'Nacos MCP Registry',
        url: 'https://nacos.io/blog/nacos-gvr7dx_awbbpb_gg16sv97bgirkixe/?spm=5238cd80.2ef5001f.0.0.3f613b7caSfxcr&source=blog',
      },
    ],
    description: $i18n.get({
      id: 'main.pages.MCP.Create.nacosDescription',
      dm: 'With Nacos MCP Router combined with Nacos MCP Registry, it automatically recommends, installs, and proxies MCP Server based on task details, eliminating complex configuration and restart operations.\nPlease enter the Nacos MCP Router configuration below. If you are unfamiliar with the specific steps, visit: Nacos MCP Registry.',
    }),
  },
  {
    title: 'Higress',
    linkButtons: [
      {
        text: 'Higress Open API To MCP',
        url: 'https://higress.ai/blog/bulk-conversion-of-existing-openapi-to-mcp-server',
      },
    ],
    description: $i18n.get({
      id: 'main.pages.MCP.Create.higressDescription',
      dm: 'You can use Higress gateway to connect internally deployed business applications and API platforms to intelligent agents. Following the Higress MCP configuration process, you can transform existing APIs into MCP services with zero code modification (currently supports HTTP and Dubbo protocols). Please enter the MCP Server address proxied through Higress below. If you are unfamiliar with the specific steps, visit: Higress Open API To MCP.',
    }),
  },
];
