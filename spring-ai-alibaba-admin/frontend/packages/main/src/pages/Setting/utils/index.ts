import $i18n from '@/i18n';

export const BAILIAN_URL = 'https://bailian.console.aliyun.com/';

export const API_KEY_TIP_SECTIONS = [
  {
    title: $i18n.get({
      id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.modelServiceProvider',
      dm: 'Model Service Provider',
    }),
    linkButtons: [
      {
        text: $i18n.get({
          id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.AliyunBailian',
          dm: 'Alibaba Cloud Bailian',
        }),
        url: 'https://bailian.console.aliyun.com/console?tab=home#/home',
      },
      {
        text: 'ModelStudio',
        url: 'https://www.alibabacloud.com/en/product/modelstudio?_p_lc=1',
      },
      { text: 'OpenAI', url: 'https://openai.com/api/' },
      { text: 'OpenRouter', url: 'https://openrouter.ai/' },
    ],
    description: $i18n.get({
      id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.providerNameDescription',
      dm: 'You can choose model service providers that comply with the OpenAI API format, register API Keys and API addresses, such as Alibaba Cloud Bailian (Mainland China), ModelStudio (Singapore), OpenAI, OpenRouter, SiliconFlow and other similar providers.',
    }),
  },
];
