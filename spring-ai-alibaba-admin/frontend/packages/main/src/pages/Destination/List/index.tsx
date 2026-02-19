import CardList from '@/components/Card/List';
import InnerLayout from '@/components/InnerLayout';
import Search from '@/components/Search';
import $i18n from '@/i18n';
import {
  deleteDestination,
  getDestinationList,
  testConnection,
} from '@/services/destination';
import { IGetDestinationListParams } from '@/types/destination';
import { AlertDialog, Button, IconFont, message } from '@spark-ai/design';
import { useMount, useSetState } from 'ahooks';
import { useRef } from 'react';
import { history } from 'umi';
import DestinationCard from './components/Card';
import styles from './index.module.less';

interface IDestinationCard {
  destination_id: string;
  name: string;
  description: string;
  provider_type: string;
  status: number;
  test_result: string;
  gmt_modified: string;
}

export default function DestinationList() {
  const [state, setState] = useSetState({
    size: 50,
    current: 1,
    total: 0,
    name: '',
    loading: true,
    list: [] as IDestinationCard[],
  });
  const isSearchRef = useRef(false);

  const fetchList = (extraParams: Partial<IGetDestinationListParams> = {}) => {
    const searchParams = isSearchRef.current ? { name: state.name } : {};
    setState({ loading: true });
    getDestinationList({
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
    setState({ current: 1 });
    fetchList({ current: 1 });
  };

  const handleDelete = (id: string) => {
    AlertDialog.warning({
      title: $i18n.get({
        id: 'main.pages.Destination.List.deleteDestination',
        dm: 'Delete Destination',
      }),
      children: $i18n.get({
        id: 'main.pages.Destination.List.confirmDeleteDestination',
        dm: 'Are you sure you want to delete this destination?',
      }),
      danger: true,
      okText: $i18n.get({
        id: 'main.pages.Destination.List.confirmDelete',
        dm: 'Confirm Delete',
      }),
      onOk: () => {
        deleteDestination(id).then(() => {
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
        id: 'main.pages.Destination.List.testingConnection',
        dm: 'Testing connection...',
      }),
      key: 'test',
    });
    testConnection(id)
      .then((result) => {
        if (result.status === 'PASS') {
          message.success({
            content: result.message || $i18n.get({
              id: 'main.pages.Destination.List.connectionSuccess',
              dm: 'Connection successful',
            }),
            key: 'test',
          });
          fetchList();
        } else {
          message.warning({
            content: result.message || 'Connection test returned unexpected result',
            key: 'test',
          });
        }
      })
      .catch(() => {
        message.error({
          content: $i18n.get({
            id: 'main.pages.Destination.List.connectionFailed',
            dm: 'Connection test failed',
          }),
          key: 'test',
        });
      });
  };

  const handleClickAction = (key: string, id: string) => {
    switch (key) {
      case 'delete':
        handleDelete(id);
        break;
      case 'edit':
        history.push(`/destination/edit/${id}`);
        break;
      case 'test':
        handleTestConnection(id);
        break;
      default:
        break;
    }
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
            id: 'main.pages.Destination.List.destinations',
            dm: 'Destinations',
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
                id: 'main.pages.Destination.List.searchPlaceholder',
                dm: 'Search destination by name',
              })}
              value={state.name}
              onChange={(val) => setState({ name: val })}
              onSearch={handleSearch}
            />
            <Button
              type="primary"
              icon={<IconFont type="spark-plus-line" className={styles['addicon']} />}
              onClick={() => history.push('/destination/create')}
            >
              {$i18n.get({
                id: 'main.pages.Destination.List.createDestination',
                dm: 'Create Destination',
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
              onClick={() => history.push('/destination/create')}
              type="primary"
            >
              {$i18n.get({
                id: 'main.pages.Destination.List.createDestination',
                dm: 'Create Destination',
              })}
            </Button>
          }
        >
          {state.list.map((item) => (
            <DestinationCard
              key={item.destination_id}
              {...item}
              handleClickAction={handleClickAction}
            />
          ))}
        </CardList>
      </div>
    </InnerLayout>
  );
}
