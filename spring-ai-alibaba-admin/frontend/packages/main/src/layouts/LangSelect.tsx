import $i18n from '@/i18n';
import { Dropdown, IconButton, IconFont } from '@spark-ai/design';
import { setWorkFlowLanguage } from '@spark-ai/flow';
import { useMount } from 'ahooks';
import { MenuProps } from 'antd';

const menuItems: MenuProps['items'] = [
  {
    key: 'en',
    label: (
      <div className="flex items-center gap-[4px]">
        <IconFont type="spark-english02-line" /> English
      </div>
    ),
  },
  {
    key: 'zh',
    label: (
      <div className="flex items-center gap-[4px]">
        <IconFont type="spark-chinese02-line" /> Simplified Chinese
      </div>
    ),
  },
];

export default function () {
  // Language selector disabled - English only
  return null;
}
