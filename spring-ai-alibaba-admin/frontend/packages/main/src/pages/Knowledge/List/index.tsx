import CardList from '@/components/Card/List';
import InnerLayout from '@/components/InnerLayout';
import Search from '@/components/Search';
import $i18n from '@/i18n';
import { deleteKnowledge, getKnowledgeList } from '@/services/knowledge';
import { IGetKnowledgeListParams } from '@/types/knowledge';
import { AlertDialog, Button, IconFont, message } from '@spark-ai/design';
import { useMount, useSetState } from 'ahooks';
import { useRef } from 'react';
import { history } from 'umi';
import KnowledgeCard from './components/Card';
import { IKnowledgeCard } from './components/Card/type';
import styles from './index.module.less';

export default function () {
  const [state, setState] = useSetState({
    size: 50,
    current: 1,
    total: 0,
    name: '',
    loading: true,
    status: '',
    list: [] as IKnowledgeCard[],
    showCreateModal: false,
    activeRecord: null,
    showEditNameModal: false,
  });
  const isSearchRef = useRef(false);
  const fetchList = (extraParams: Partial<IGetKnowledgeListParams> = {}) => {
    const searchParams = isSearchRef.current
      ? {
          name: state.name,
        }
      : {};
    setState({
      loading: true,
    });
    getKnowledgeList({
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
        setState({
          loading: false,
        });
      });
  };

  useMount(() => {
    fetchList();
  });
  const handleSearch = (val: string) => {
    isSearchRef.current = !!val;
    setState({
      current: 1,
    });
    fetchList({
      current: 1,
    });
  };
  const handleDelete = (id: string) => {
    AlertDialog.warning({
      title: $i18n.get({
        id: 'main.pages.Knowledge.List.index.deleteData',
        dm: 'Delete Knowledge Base',
      }),
      children: $i18n.get({
        id: 'main.pages.Knowledge.List.index.confirmDeleteData',
        dm: 'This will permanently delete this knowledge base and all associated data including document indices, authority indices, RAG vector indices, and crawl jobs. This action cannot be undone.',
      }),
      danger: true,
      okText: $i18n.get({
        id: 'main.pages.Knowledge.List.index.confirmDelete',
        dm: 'Delete Everything',
      }),
      onOk: () => {
        deleteKnowledge(id).then(() => {
          message.success('Knowledge base deleted. Associated indices are being cleaned up in the background.');
          let current = state.current;
          if (state.list.length === 1 && current > 1) {
            current -= 1;
            setState({
              current,
            });
          }
          fetchList({
            current,
          });
        }).catch((e: any) => { message.error(e?.message || 'Operation failed'); });
      },
    });
  };
  const handleClickAction = (key: string, id: string) => {
    switch (key) {
      case 'delete':
        handleDelete(id);
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
            id: 'main.pages.Knowledge.List.index.knowledgeBase',
            dm: 'Knowledge Base',
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
                id: 'main.pages.Knowledge.List.index.enterKnowledgeBaseName',
                dm: 'Please enter knowledge base name',
              })}
              value={state.name}
              onChange={(val) => setState({ name: val })}
              onSearch={handleSearch}
            />
            <Button
              type="primary"
              icon={<IconFont type="spark-plus-line" className={styles['addicon']} />}
              onClick={() => history.push('/knowledge/create')}
            >
              {$i18n.get({
                id: 'main.pages.Knowledge.List.index.createKnowledgeBase',
                dm: 'Create Knowledge Base',
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
              setState({
                current,
                size,
              });
              fetchList({
                current,
                size,
              });
            },
          }}
          isSearch={isSearchRef.current}
          loading={state.loading}
          emptyAction={
            <Button
              onClick={() => history.push('/knowledge/create')}
              type="primary"
            >
              {$i18n.get({
                id: 'main.pages.Knowledge.List.index.createKnowledgeBase',
                dm: 'Create Knowledge Base',
              })}
            </Button>
          }
        >
          {state.list.map((item) => {
            return (
              <KnowledgeCard
                key={item.kb_id}
                {...item}
                handleClickAction={handleClickAction}
              />
            );
          })}
        </CardList>
      </div>
    </InnerLayout>
  );
}
