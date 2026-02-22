import CardList from '@/components/Card/List';
import InnerLayout from '@/components/InnerLayout';
import Search from '@/components/Search';
import $i18n from '@/i18n';
import {
  deleteProvider,
  getProviderHealth,
  listProviders,
  updateProvider,
} from '@/services/modelService';
import { IProvider } from '@/types/modelService';
import { AlertDialog, Button, IconFont, message } from '@spark-ai/design';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'umi';
import ModelServiceCard from './components/ModelServiceCard';
import ModelServiceProviderModal from './components/ModelServiceProviderModal';
import styles from './index.module.less';

const ModelService = () => {
  const navigate = useNavigate();
  const [providers, setProviders] = useState<IProvider[]>([]);
  const [healthMap, setHealthMap] = useState<Record<string, boolean>>({});
  const [loading, setLoading] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchText, setSearchText] = useState('');

  const filteredProviders = useMemo(() => {
    if (!searchText.trim()) return providers;
    const lower = searchText.toLowerCase();
    return providers.filter((p) =>
      p.name?.toLowerCase().includes(lower) || p.provider?.toLowerCase().includes(lower)
    );
  }, [providers, searchText]);

  useEffect(() => {
    fetchProviders();
  }, []);

  const fetchProviders = async () => {
    try {
      setLoading(true);
      const res = await listProviders();
      setProviders(res?.data || []);
    } finally {
      setLoading(false);
    }
    // Fetch live health in the background (non-blocking)
    getProviderHealth()
      .then((res) => setHealthMap(res?.data || {}))
      .catch(() => {});
  };

  const handleServiceClick = (action?: string, provider?: IProvider) => {
    if (!action || !provider) return;
    switch (action) {
      case 'detail':
      case 'edit':
        navigate(`/setting/modelService/${provider.provider}`);
        break;
      case 'delete':
        AlertDialog.warning({
          title: $i18n.get({
            id: 'main.pages.Setting.ModelService.index.delete',
            dm: 'Delete',
          }),
          content: $i18n.get({
            id: 'main.pages.Setting.ModelService.index.confirmDeleteModelServiceProvider',
            dm: 'Confirm delete this model service provider?',
          }),
          onOk: () => {
            handleDeleteService(provider);
          },
        });
        break;
      case 'start':
        handleEnableService(provider, true);
        break;
      case 'stop':
        handleEnableService(provider, false);
        break;
    }
  };

  const handleDeleteService = (provider: IProvider) => {
    deleteProvider(provider.provider).then((res) => {
      if (res.data) {
        message.success(
          $i18n.get({
            id: 'main.pages.Setting.ModelService.index.deleteSuccess',
            dm: 'Deleted successfully',
          }),
        );
        fetchProviders();
      }
    });
  };

  const handleEnableService = (provider: IProvider, enable: boolean) => {
    updateProvider(provider.provider, { ...provider, enable }).then((res) => {
      if (res.data) {
        message.success(
          enable
            ? $i18n.get({
                id: 'main.pages.Setting.ModelService.index.startSuccess',
                dm: 'Started successfully',
              })
            : $i18n.get({
                id: 'main.pages.Setting.ModelService.index.stopSuccess',
                dm: 'Stopped successfully',
              }),
        );
        fetchProviders();
      }
    });
  };
  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: $i18n.get({
            id: 'main.pages.App.index.home',
            dm: 'Home',
          }),
          path: '/',
        },
        {
          title: $i18n.get({
            id: 'main.pages.Setting.ModelService.index.modelServiceManagement',
            dm: 'Model Service Management',
          }),
        },
      ]}
      left={filteredProviders.length}
      styles={{
        breadcrumb: {
          maxWidth: 300,
        },
      }}
    >
      <div className={styles['search-wrapper']}>
        <Search
          placeholder={$i18n.get({
            id: 'main.pages.Setting.ModelService.index.searchPlaceholder',
            dm: 'Search model service by name',
          })}
          value={searchText}
          onChange={(val: string) => setSearchText(val)}
          onSearch={() => {}}
        />
        <Button
          type="primary"
          icon={<IconFont type="spark-plus-line" className={styles['addicon']} />}
          onClick={() => setIsModalOpen(true)}
        >
          {$i18n.get({
            id: 'main.pages.Setting.ModelService.index.addModelServiceProvider',
            dm: 'Add Model Service Provider',
          })}
        </Button>
      </div>
      <div className={styles.container}>
        <CardList loading={loading}>
          {filteredProviders.map((provider) => (
            <ModelServiceCard
              key={provider.provider}
              service={provider}
              reachable={healthMap[provider.provider]}
              onClick={handleServiceClick}
            />
          ))}
        </CardList>
      </div>
      <ModelServiceProviderModal
        open={isModalOpen}
        onCancel={() => setIsModalOpen(false)}
        onSuccess={() => {
          setIsModalOpen(false);
          fetchProviders();
        }}
      />
    </InnerLayout>
  );
};

export default ModelService;
