import CardList from '@/components/Card/List';
import InnerLayout from '@/components/InnerLayout';
import Search from '@/components/Search';
import $i18n from '@/i18n';
import {
  copySource,
  deleteSource,
  getSourceList,
  startSync,
  testConnection,
} from '@/services/source';
import { IGetSourceListParams } from '@/types/source';
import { AlertDialog, Button, IconFont, message } from '@spark-ai/design';
import { useMount, useSetState } from 'ahooks';
import { useRef } from 'react';
import { history } from 'umi';
import SourceCard from './components/Card';
import { ISourceCard } from './components/Card/type';
import styles from './index.module.less';

export default function SourceList() {
  const [state, setState] = useSetState({
    size: 50,
    current: 1,
    total: 0,
    name: '',
    loading: true,
    list: [] as ISourceCard[],
  });
  const isSearchRef = useRef(false);

  const fetchList = (extraParams: Partial<IGetSourceListParams> = {}) => {
    const searchParams = isSearchRef.current ? { name: state.name } : {};
    setState({ loading: true });
    getSourceList({
      size: state.size,
      current: state.current,
      ...searchParams,
      ...extraParams,
    })
      .then((res) => {
        setState({
          list: res.records,
          total: res.total,
          loading: false,
        });
      })
      .catch(() => {
        setState({ loading: false });
      });
  };

  useMount(() => {
    fetchList();
  });

  const handleSearch = (val: string) => {
    isSearchRef.current = !!val;
    setState({ current: 1, name: val });
    fetchList({ current: 1, name: val });
  };

  const handleDelete = (id: string) => {
    AlertDialog.warning({
      title: $i18n.get({
        id: 'main.pages.Source.List.index.deleteSource',
        dm: 'Delete Source',
      }),
      children: $i18n.get({
        id: 'main.pages.Source.List.index.confirmDeleteSource',
        dm: 'Are you sure you want to delete this source? All associated crawl jobs will also be removed.',
      }),
      danger: true,
      okText: $i18n.get({
        id: 'main.pages.Source.List.index.confirmDelete',
        dm: 'Confirm Delete',
      }),
      onOk: () => {
        deleteSource(id).then(() => {
          let current = state.current;
          if (state.list.length === 1 && current > 1) {
            current -= 1;
            setState({ current });
          }
          fetchList({ current });
        });
      },
    });
  };

  const handleTestConnection = (id: string) => {
    message.loading({
      content: $i18n.get({
        id: 'main.pages.Source.List.index.testingConnection',
        dm: 'Testing connection...',
      }),
      key: 'test',
    });
    testConnection(id)
      .then((result) => {
        if (result.result && result.result.includes('Connection working')) {
          message.success({
            content: $i18n.get({
              id: 'main.pages.Source.List.index.connectionSuccess',
              dm: 'Connection successful',
            }),
            key: 'test',
          });
        } else {
          message.warning({
            content: result.result || 'Connection test returned unexpected result',
            key: 'test',
          });
        }
      })
      .catch(() => {
        message.error({
          content: $i18n.get({
            id: 'main.pages.Source.List.index.connectionFailed',
            dm: 'Connection test failed',
          }),
          key: 'test',
        });
      });
  };

  const handleCopy = (id: string) => {
    message.loading({
      content: $i18n.get({
        id: 'main.pages.Source.List.index.copyingSource',
        dm: 'Copying source...',
      }),
      key: 'copy',
    });
    copySource(id)
      .then((newId) => {
        message.success({
          content: $i18n.get({
            id: 'main.pages.Source.List.index.copySuccess',
            dm: 'Source copied successfully',
          }),
          key: 'copy',
        });
        // Navigate to edit the new copy
        history.push(`/source/edit/${newId}`);
      })
      .catch(() => {
        message.error({
          content: $i18n.get({
            id: 'main.pages.Source.List.index.copyFailed',
            dm: 'Failed to copy source',
          }),
          key: 'copy',
        });
      });
  };

  const handleClickAction = (key: string, id: string) => {
    switch (key) {
      case 'delete':
        handleDelete(id);
        break;
      case 'edit':
        history.push(`/source/edit/${id}`);
        break;
      case 'copy':
        handleCopy(id);
        break;
      case 'test':
        handleTestConnection(id);
        break;
      case 'sync':
        handleStartSync(id);
        break;
      default:
        break;
    }
  };

  const handleStartSync = (id: string) => {
    message.loading({
      content: $i18n.get({
        id: 'main.pages.Source.List.index.startingSync',
        dm: 'Starting sync...',
      }),
      key: 'sync',
    });
    startSync(id)
      .then(() => {
        message.success({
          content: $i18n.get({
            id: 'main.pages.Source.List.index.syncStarted',
            dm: 'Sync started successfully',
          }),
          key: 'sync',
        });
        // Refresh list to show updated status
        fetchList();
      })
      .catch(() => {
        message.error({
          content: $i18n.get({
            id: 'main.pages.Source.List.index.syncFailed',
            dm: 'Failed to start sync',
          }),
          key: 'sync',
        });
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
            id: 'main.pages.Source.List.index.sources',
            dm: 'Sources',
          }),
        },
      ]}
      left={state.total}
    >
      <div>
        {!state.list.length && !state.loading && !isSearchRef.current ? null : (
          <div className={styles['search-wrapper']}>
            <Search
              placeholder={$i18n.get({
                id: 'main.pages.Source.List.index.searchPlaceholder',
                dm: 'Search source by name',
              })}
              value={state.name}
              onChange={(val) => setState({ name: val })}
              onSearch={handleSearch}
            />
            <Button
              type="primary"
              icon={<IconFont type="spark-plus-line" className={styles['addicon']} />}
              onClick={() => history.push('/source/create')}
            >
              {$i18n.get({
                id: 'main.pages.Source.List.index.createSource',
                dm: 'Create Source',
              })}
            </Button>
          </div>
        )}
        <CardList
          pagination={{
            current: state.current,
            total: state.total,
            pageSize: state.size,
            onChange: (current, size) => {
              setState({ current, size });
              fetchList({ current, size });
            },
          }}
          isSearch={isSearchRef.current}
          loading={state.loading}
          emptyAction={
            <Button
              onClick={() => history.push('/source/create')}
              type="primary"
            >
              {$i18n.get({
                id: 'main.pages.Source.List.index.createSource',
                dm: 'Create Source',
              })}
            </Button>
          }
        >
          {state.list.map((item) => (
            <SourceCard
              key={item.source_id}
              {...item}
              handleClickAction={handleClickAction}
            />
          ))}
        </CardList>
      </div>
    </InnerLayout>
  );
}
