import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import { createSource, getConnectorTypes, getSourceDetail, updateSource, testConnection, testGroupApi, testQuery, enableSource } from '@/services/source';
import { AlertDialog, Button, message } from '@spark-ai/design';
import { useMount, useSetState } from 'ahooks';
import { Collapse, Input, Select, Steps, Switch, Table, Tag } from 'antd';
import classNames from 'classnames';
import React, { useState } from 'react';
import { history, useParams } from 'umi';
import styles from './index.module.less';

/* ──────────────────────── Types ──────────────────────── */

interface ConnectorType {
  description: string;
  class_name: string;
}

interface ConfigEntry {
  key: string;
  value: string;
}

/** Definition of a single form field shown for a connector */
interface FieldDef {
  /** ManifoldCF _PARAMETER_ key (case-sensitive, must match Java source) */
  key: string;
  /** Human label */
  label: string;
  /** 'text' | 'password' | 'select' | 'switch' | 'textarea' | 'number' */
  type: 'text' | 'password' | 'select' | 'switch' | 'textarea' | 'number';
  /** Default value */
  defaultValue: string;
  /** Required? */
  required?: boolean;
  /** Placeholder hint */
  placeholder?: string;
  /** Select options (for type=select) */
  options?: { label: string; value: string }[];
  /** Group header — fields after this belong to a collapsible section */
  group?: string;
}

interface FormState {
  step: number;
  name: string;
  description: string;
  selectedConnector: ConnectorType | null;
  selectedVendor: string | null;
  configValues: Record<string, string>;
  extraEntries: ConfigEntry[];
  connectorTypes: ConnectorType[];
  loading: boolean;
  testResult: string | null;
  testPassed: boolean;
  createdSourceId: string | null;
  /** ACL enforcement */
  enforceAcl: boolean;
  aclGroupApiUrl: string;
  aclGroupMembersApiUrl: string;
  aclTestResult: string | null;
  aclTestPassed: boolean;
  /** Query test results (CMIS query / REST API seed / Group API / User API) */
  queryTestLoading: boolean;
  activeTestType: string | null;
  queryTestResult: string | null;
  queryTestItems: Record<string, any>[];
  queryTestCount: number;
  /** Edit mode */
  editDataLoaded: boolean;
}

/* ──────────────────────── Connector class constants ──────────────────────── */

const CLS = {
  CMIS: 'org.apache.manifoldcf.crawler.connectors.cmis.CmisRepositoryConnector',
  RESTAPI: 'org.apache.manifoldcf.crawler.connectors.restapi.RestApiRepositoryConnector',
  CONFLUENCE: 'org.apache.manifoldcf.crawler.connectors.confluence.v2.ConfluenceConnector',
  CONFLUENCE_V6: 'org.apache.manifoldcf.crawler.connectors.confluence.v2.ConfluenceConnector',
  AMAZONS3: 'org.apache.manifoldcf.crawler.connectors.amazons3.AmazonS3Connector',
  JIRA: 'org.apache.manifoldcf.crawler.connectors.jira.JiraRepositoryConnector',
  JDBC: 'org.apache.manifoldcf.crawler.connectors.jdbc.JDBCConnector',
  DROPBOX: 'org.apache.manifoldcf.crawler.connectors.dropbox.DropboxRepositoryConnector',
  GOOGLEDRIVE: 'org.apache.manifoldcf.crawler.connectors.googledrive.GoogleDriveRepositoryConnector',
  SHAREPOINT: 'org.apache.manifoldcf.crawler.connectors.sharepoint.SharePointRepository',
  EMAIL: 'org.apache.manifoldcf.crawler.connectors.email.EmailConnector',
  WEB: 'org.apache.manifoldcf.crawler.connectors.webcrawler.WebcrawlerConnector',
  FILESYSTEM: 'org.apache.manifoldcf.crawler.connectors.filesystem.FileConnector',
  WIKI: 'org.apache.manifoldcf.crawler.connectors.wiki.WikiConnector',
  RSS: 'org.apache.manifoldcf.crawler.connectors.rss.RSSConnector',
  HDFS: 'org.apache.manifoldcf.crawler.connectors.hdfs.HDFSRepositoryConnector',
  GRIDFTP: 'org.apache.manifoldcf.crawler.connectors.gridftp.GridFTPRepositoryConnector',
};

/* ──────────────────────── Field definitions per connector ──────────────────────── */

const CMIS_FIELDS: FieldDef[] = [
  { key: 'username', label: 'Username', type: 'text', defaultValue: '', required: true, placeholder: 'admin' },
  { key: 'password', label: 'Password', type: 'password', defaultValue: '', required: true },
  { key: 'protocol', label: 'Protocol', type: 'select', defaultValue: 'https', required: true,
    options: [{ label: 'HTTPS', value: 'https' }, { label: 'HTTP', value: 'http' }] },
  { key: 'server', label: 'Server', type: 'text', defaultValue: '', required: true, placeholder: 'alfresco.example.com' },
  { key: 'port', label: 'Port', type: 'number', defaultValue: '443', required: true },
  { key: 'path', label: 'CMIS API Path', type: 'text', defaultValue: '/alfresco/api/-default-/cmis/versions/1.1/atom', required: true,
    placeholder: '/alfresco/api/-default-/cmis/versions/1.1/atom' },
  { key: 'binding', label: 'Binding', type: 'select', defaultValue: 'atom', required: true,
    options: [{ label: 'AtomPub', value: 'atom' }, { label: 'Browser (JSON)', value: 'browser' }, { label: 'Web Services', value: 'ws' }] },
  { key: 'repositoryId', label: 'Repository ID', type: 'text', defaultValue: '-default-', placeholder: '-default-' },
  { key: 'maxFileSize', label: 'Max File Size (KB, 0=unlimited)', type: 'number', defaultValue: '0', group: 'Advanced' },
  { key: 'cmisQuery', label: 'CMIS Query', type: 'text', defaultValue: '', placeholder: "SELECT * FROM cmis:document", group: 'Advanced' },
];

const AMAZONS3_FIELDS: FieldDef[] = [
  { key: 'aws_access_key', label: 'AWS Access Key', type: 'text', defaultValue: '', required: true },
  { key: 'aws_secret_key', label: 'AWS Secret Key', type: 'password', defaultValue: '', required: true },
  { key: 'amazons3_protocol', label: 'Protocol', type: 'select', defaultValue: 'https',
    options: [{ label: 'HTTPS', value: 'https' }, { label: 'HTTP', value: 'http' }] },
  { key: 'amazons3_host', label: 'S3 Host', type: 'text', defaultValue: '', placeholder: 's3.amazonaws.com (leave blank for AWS default)' },
  { key: 'amazons3_port', label: 'S3 Port', type: 'number', defaultValue: '' },
  { key: 'amazons3_proxy_host', label: 'Proxy Host', type: 'text', defaultValue: '', group: 'Proxy Settings' },
  { key: 'amazons3_proxy_port', label: 'Proxy Port', type: 'number', defaultValue: '', group: 'Proxy Settings' },
  { key: 'amazons3_proxy_domain', label: 'Proxy Domain', type: 'text', defaultValue: '', group: 'Proxy Settings' },
  { key: 'amazons3_proxy_username', label: 'Proxy Username', type: 'text', defaultValue: '', group: 'Proxy Settings' },
  { key: 'amazons3_proxy_password', label: 'Proxy Password', type: 'password', defaultValue: '', group: 'Proxy Settings' },
];

const CONFLUENCE_FIELDS: FieldDef[] = [
  { key: 'username', label: 'Username', type: 'text', defaultValue: '', required: true },
  { key: 'password', label: 'Password / API Token', type: 'password', defaultValue: '', required: true },
  { key: 'protocol', label: 'Protocol', type: 'select', defaultValue: 'https',
    options: [{ label: 'HTTPS', value: 'https' }, { label: 'HTTP', value: 'http' }] },
  { key: 'host', label: 'Host', type: 'text', defaultValue: '', required: true, placeholder: 'confluence.example.com' },
  { key: 'port', label: 'Port', type: 'number', defaultValue: '443' },
  { key: 'path', label: 'Path', type: 'text', defaultValue: '/wiki', placeholder: '/wiki' },
];

const CONFLUENCE_V6_FIELDS: FieldDef[] = [
  ...CONFLUENCE_FIELDS,
  { key: 'socket_timeout', label: 'Socket Timeout (ms)', type: 'number', defaultValue: '', group: 'Timeouts' },
  { key: 'connection_timeout', label: 'Connection Timeout (ms)', type: 'number', defaultValue: '', group: 'Timeouts' },
  { key: 'retryNumber', label: 'Retry Count', type: 'number', defaultValue: '', group: 'Timeouts' },
  { key: 'retryInterval', label: 'Retry Interval (ms)', type: 'number', defaultValue: '', group: 'Timeouts' },
  { key: 'proxy_host', label: 'Proxy Host', type: 'text', defaultValue: '', group: 'Proxy Settings' },
  { key: 'proxy_port', label: 'Proxy Port', type: 'number', defaultValue: '', group: 'Proxy Settings' },
  { key: 'proxy_protocol', label: 'Proxy Protocol', type: 'select', defaultValue: '',
    options: [{ label: 'HTTPS', value: 'https' }, { label: 'HTTP', value: 'http' }], group: 'Proxy Settings' },
  { key: 'proxy_username', label: 'Proxy Username', type: 'text', defaultValue: '', group: 'Proxy Settings' },
  { key: 'proxy_password', label: 'Proxy Password', type: 'password', defaultValue: '', group: 'Proxy Settings' },
];

const JIRA_FIELDS: FieldDef[] = [
  { key: 'clientid', label: 'Client ID / Username', type: 'text', defaultValue: '', required: true },
  { key: 'clientsecret', label: 'Client Secret / API Token', type: 'password', defaultValue: '', required: true },
  { key: 'jiraprotocol', label: 'Protocol', type: 'select', defaultValue: 'https',
    options: [{ label: 'HTTPS', value: 'https' }, { label: 'HTTP', value: 'http' }] },
  { key: 'jirahost', label: 'Host', type: 'text', defaultValue: '', required: true, placeholder: 'jira.example.com' },
  { key: 'jiraport', label: 'Port', type: 'number', defaultValue: '443' },
  { key: 'jirapath', label: 'Path', type: 'text', defaultValue: '', placeholder: '' },
  { key: 'jiraquery', label: 'JQL Query', type: 'text', defaultValue: '', placeholder: 'project=MYPROJECT' },
  { key: 'jiraproxyhost', label: 'Proxy Host', type: 'text', defaultValue: '', group: 'Proxy Settings' },
  { key: 'jiraproxyport', label: 'Proxy Port', type: 'number', defaultValue: '', group: 'Proxy Settings' },
  { key: 'jiraproxydomain', label: 'Proxy Domain', type: 'text', defaultValue: '', group: 'Proxy Settings' },
  { key: 'jiraproxyusername', label: 'Proxy Username', type: 'text', defaultValue: '', group: 'Proxy Settings' },
  { key: 'jiraproxypassword', label: 'Proxy Password', type: 'password', defaultValue: '', group: 'Proxy Settings' },
];

const JDBC_FIELDS: FieldDef[] = [
  { key: 'JDBC Provider', label: 'JDBC Provider', type: 'select', defaultValue: 'oracle:thin:', required: true,
    options: [
      { label: 'Oracle (thin)', value: 'oracle:thin:' },
      { label: 'PostgreSQL', value: 'postgresql:' },
      { label: 'MySQL', value: 'mysql:' },
      { label: 'MS SQL Server', value: 'jtds:sqlserver:' },
      { label: 'Sybase', value: 'jtds:sybase:' },
    ] },
  { key: 'Host', label: 'Host', type: 'text', defaultValue: '', required: true, placeholder: 'db.example.com' },
  { key: 'Database name', label: 'Database Name', type: 'text', defaultValue: '', required: true },
  { key: 'User name', label: 'Username', type: 'text', defaultValue: '', required: true },
  { key: 'Password', label: 'Password', type: 'password', defaultValue: '', required: true },
  { key: 'Raw driver string', label: 'Raw JDBC Connection String', type: 'text', defaultValue: '',
    placeholder: 'jdbc:provider://host:port/database (overrides above if set)', group: 'Advanced' },
  { key: 'JDBC column access method', label: 'Column Access Method', type: 'select', defaultValue: 'String',
    options: [{ label: 'String', value: 'String' }, { label: 'Stream', value: 'Stream' }], group: 'Advanced' },
];

const DROPBOX_FIELDS: FieldDef[] = [
  { key: 'app_key', label: 'App Key', type: 'text', defaultValue: '', required: true },
  { key: 'app_secret', label: 'App Secret', type: 'password', defaultValue: '', required: true },
  { key: 'key', label: 'OAuth Key (Access Token)', type: 'text', defaultValue: '', required: true },
  { key: 'secret', label: 'OAuth Secret', type: 'password', defaultValue: '', required: true },
  { key: 'dropboxpath', label: 'Dropbox Path', type: 'text', defaultValue: '/', placeholder: '/' },
];

const GOOGLEDRIVE_FIELDS: FieldDef[] = [
  { key: 'clientid', label: 'Client ID', type: 'text', defaultValue: '', required: true },
  { key: 'clientsecret', label: 'Client Secret', type: 'password', defaultValue: '', required: true },
  { key: 'refreshtoken', label: 'Refresh Token', type: 'password', defaultValue: '', required: true },
  { key: 'googledriveQuery', label: 'Drive Query', type: 'text', defaultValue: '',
    placeholder: "mimeType != 'application/vnd.google-apps.folder'" },
];

const SHAREPOINT_FIELDS: FieldDef[] = [
  { key: 'serverVersion', label: 'Server Version', type: 'select', defaultValue: '4.0', required: true,
    options: [
      { label: 'SharePoint 2007 (2.0)', value: '2.0' },
      { label: 'SharePoint 2010 (3.0)', value: '3.0' },
      { label: 'SharePoint 2013/2016/2019 (4.0)', value: '4.0' },
    ] },
  { key: 'serverProtocol', label: 'Protocol', type: 'select', defaultValue: 'https',
    options: [{ label: 'HTTPS', value: 'https' }, { label: 'HTTP', value: 'http' }] },
  { key: 'serverName', label: 'Server Name', type: 'text', defaultValue: '', required: true, placeholder: 'sharepoint.example.com' },
  { key: 'serverPort', label: 'Port', type: 'number', defaultValue: '443' },
  { key: 'serverLocation', label: 'Site Path', type: 'text', defaultValue: '', placeholder: '/sites/mysite' },
  { key: 'userName', label: 'Username', type: 'text', defaultValue: '', required: true, placeholder: 'domain\\user or user@domain' },
  { key: 'password', label: 'Password', type: 'password', defaultValue: '', required: true },
  { key: 'keystore', label: 'SSL Keystore', type: 'text', defaultValue: '', group: 'SSL & Proxy' },
  { key: 'authorityType', label: 'Authority Type', type: 'select', defaultValue: 'ActiveDirectory',
    options: [
      { label: 'Active Directory', value: 'ActiveDirectory' },
      { label: 'Claims Based', value: 'ClaimSpace' },
    ], group: 'SSL & Proxy' },
  { key: 'proxyHost', label: 'Proxy Host', type: 'text', defaultValue: '', group: 'SSL & Proxy' },
  { key: 'proxyPort', label: 'Proxy Port', type: 'number', defaultValue: '', group: 'SSL & Proxy' },
  { key: 'proxyUser', label: 'Proxy Username', type: 'text', defaultValue: '', group: 'SSL & Proxy' },
  { key: 'proxyPassword', label: 'Proxy Password', type: 'password', defaultValue: '', group: 'SSL & Proxy' },
  { key: 'proxyDomain', label: 'Proxy Domain', type: 'text', defaultValue: '', group: 'SSL & Proxy' },
];

const EMAIL_FIELDS: FieldDef[] = [
  { key: 'username', label: 'Username', type: 'text', defaultValue: '', required: true, placeholder: 'user@example.com' },
  { key: 'password', label: 'Password', type: 'password', defaultValue: '', required: true },
  { key: 'protocol', label: 'Protocol', type: 'select', defaultValue: 'IMAP', required: true,
    options: [
      { label: 'IMAP', value: 'IMAP' },
      { label: 'IMAP-SSL', value: 'IMAP-SSL' },
      { label: 'POP3', value: 'POP3' },
      { label: 'POP3-SSL', value: 'POP3-SSL' },
    ] },
  { key: 'server', label: 'Server', type: 'text', defaultValue: '', required: true, placeholder: 'imap.example.com' },
  { key: 'port', label: 'Port', type: 'number', defaultValue: '993' },
  { key: 'url', label: 'Properties', type: 'textarea', defaultValue: '', placeholder: 'key=value (one per line)', group: 'Advanced' },
  { key: 'attachmenturl', label: 'Attachment URL Template', type: 'text', defaultValue: '', group: 'Advanced' },
];

const WEB_CRAWLER_FIELDS: FieldDef[] = [
  { key: 'Email address', label: 'Email Address', type: 'text', defaultValue: '', required: true,
    placeholder: 'admin@example.com (for robots.txt compliance)' },
  { key: 'Robots usage', label: 'Robots.txt Usage', type: 'select', defaultValue: 'all',
    options: [
      { label: 'All (follow all)', value: 'all' },
      { label: 'None (ignore all)', value: 'none' },
    ] },
  { key: 'Meta robots tags usage', label: 'Meta Robots Tags', type: 'select', defaultValue: 'all',
    options: [
      { label: 'All (follow all)', value: 'all' },
      { label: 'None (ignore all)', value: 'none' },
    ] },
  { key: 'User-Agent platform', label: 'User-Agent Platform', type: 'text', defaultValue: '',
    placeholder: 'Custom User-Agent string' },
  { key: 'Proxy host', label: 'Proxy Host', type: 'text', defaultValue: '', group: 'Proxy Settings' },
  { key: 'Proxy port', label: 'Proxy Port', type: 'number', defaultValue: '', group: 'Proxy Settings' },
  { key: 'Proxy authentication domain', label: 'Proxy Auth Domain', type: 'text', defaultValue: '', group: 'Proxy Settings' },
  { key: 'Proxy authentication user name', label: 'Proxy Username', type: 'text', defaultValue: '', group: 'Proxy Settings' },
  { key: 'Proxy authentication password', label: 'Proxy Password', type: 'password', defaultValue: '', group: 'Proxy Settings' },
];

/** REST API uses UPPERCASE params — kept as-is from RestApiConfig.java */
const RESTAPI_FIELDS: FieldDef[] = [
  // Auth section
  { key: 'AUTHTYPE', label: 'Authentication Type', type: 'select', defaultValue: 'none', required: true,
    options: [
      { label: 'None', value: 'none' },
      { label: 'Basic Auth', value: 'basic' },
      { label: 'Bearer Token', value: 'bearer' },
      { label: 'API Key Header', value: 'apikey' },
      { label: 'OAuth 2.0', value: 'oauth2' },
    ] },
  { key: 'USERNAME', label: 'Username', type: 'text', defaultValue: '', placeholder: 'For Basic auth' },
  { key: 'PASSWORD', label: 'Password', type: 'password', defaultValue: '' },
  { key: 'APIKEY', label: 'API Key / Bearer Token', type: 'password', defaultValue: '' },
  { key: 'APIKEYHEADER', label: 'API Key Header Name', type: 'text', defaultValue: 'Authorization', placeholder: 'Authorization' },
  // Server section
  { key: 'PROTOCOL', label: 'Protocol', type: 'select', defaultValue: 'https',
    options: [{ label: 'HTTPS', value: 'https' }, { label: 'HTTP', value: 'http' }] },
  { key: 'SERVER', label: 'Server', type: 'text', defaultValue: '', required: true, placeholder: 'api.example.com' },
  { key: 'PORT', label: 'Port', type: 'number', defaultValue: '443' },
  { key: 'BASEPATH', label: 'Base Path', type: 'text', defaultValue: '', placeholder: '/api/v1' },
  // Endpoints
  { key: 'SEEDENDPOINT', label: 'Seed / List Endpoint', type: 'text', defaultValue: '', required: true, placeholder: '/items' },
  { key: 'DOCENDPOINT', label: 'Document Endpoint', type: 'text', defaultValue: '', placeholder: '/items/{id}' },
  { key: 'CONTENTENDPOINT', label: 'Content/Download Endpoint', type: 'text', defaultValue: '', placeholder: '/items/{id}/content' },
  { key: 'ACLENDPOINT', label: 'ACL Endpoint', type: 'text', defaultValue: '', placeholder: '/items/{id}/permissions' },
  // Pagination
  { key: 'PAGINATIONTYPE', label: 'Pagination Type', type: 'select', defaultValue: 'offset',
    options: [
      { label: 'Offset', value: 'offset' },
      { label: 'Page Number', value: 'page' },
      { label: 'Cursor', value: 'cursor' },
      { label: 'Link (next URL)', value: 'link' },
      { label: 'None', value: 'none' },
    ] },
  { key: 'PAGESIZE', label: 'Page Size', type: 'number', defaultValue: '100' },
  { key: 'OFFSETPARAM', label: 'Offset Param Name', type: 'text', defaultValue: 'offset', group: 'Pagination Details' },
  { key: 'LIMITPARAM', label: 'Limit Param Name', type: 'text', defaultValue: 'limit', group: 'Pagination Details' },
  { key: 'PAGEPARAM', label: 'Page Param Name', type: 'text', defaultValue: 'page', group: 'Pagination Details' },
  { key: 'CURSORFIELD', label: 'Cursor JSONPath', type: 'text', defaultValue: '', placeholder: '$.next_cursor', group: 'Pagination Details' },
  { key: 'CURSORPARAM', label: 'Cursor Query Param', type: 'text', defaultValue: '', placeholder: 'cursor', group: 'Pagination Details' },
  // JSONPath field mappings
  { key: 'ITEMSPATH', label: 'Items Array JSONPath', type: 'text', defaultValue: '$.results', required: true, group: 'Field Mappings' },
  { key: 'IDFIELD', label: 'ID Field JSONPath', type: 'text', defaultValue: '$.id', required: true, group: 'Field Mappings' },
  { key: 'TITLEFIELD', label: 'Title Field JSONPath', type: 'text', defaultValue: '$.name', group: 'Field Mappings' },
  { key: 'CONTENTFIELD', label: 'Content Field JSONPath', type: 'text', defaultValue: '', group: 'Field Mappings' },
  { key: 'MIMETYPEFIELD', label: 'MIME Type JSONPath', type: 'text', defaultValue: '', group: 'Field Mappings' },
  { key: 'CREATEDDATEFIELD', label: 'Created Date JSONPath', type: 'text', defaultValue: '', group: 'Field Mappings' },
  { key: 'MODIFIEDDATEFIELD', label: 'Modified Date JSONPath', type: 'text', defaultValue: '', group: 'Field Mappings' },
  { key: 'SIZEFIELD', label: 'Size Field JSONPath', type: 'text', defaultValue: '', group: 'Field Mappings' },
  { key: 'DOWNLOADURLFIELD', label: 'Download URL JSONPath', type: 'text', defaultValue: '', group: 'Field Mappings' },
  // ACL
  { key: 'ACLALLOWFIELD', label: 'ACL Allow JSONPath', type: 'text', defaultValue: '', group: 'ACL Mappings' },
  { key: 'ACLDENYFIELD', label: 'ACL Deny JSONPath', type: 'text', defaultValue: '', group: 'ACL Mappings' },
  { key: 'ACLPRINCIPALFIELD', label: 'ACL Principal JSONPath', type: 'text', defaultValue: '', group: 'ACL Mappings' },
  // Advanced
  { key: 'MAXFILESIZE', label: 'Max File Size (KB, 0=unlimited)', type: 'number', defaultValue: '0', group: 'Advanced' },
  { key: 'CUSTOMHEADERS', label: 'Custom Headers (key=value, one per line)', type: 'textarea', defaultValue: '', group: 'Advanced' },
  { key: 'RESPONSETYPE', label: 'Response Type', type: 'select', defaultValue: 'json',
    options: [{ label: 'JSON', value: 'json' }, { label: 'XML', value: 'xml' }], group: 'Advanced' },
  { key: 'VENDOR', label: 'Vendor Preset', type: 'text', defaultValue: 'other', group: 'Advanced' },
];

/* ──────────────────────── Connector → field definitions map ──────────────────────── */

const CONNECTOR_FIELDS: Record<string, FieldDef[]> = {
  [CLS.CMIS]: CMIS_FIELDS,
  [CLS.RESTAPI]: RESTAPI_FIELDS,
  [CLS.CONFLUENCE]: CONFLUENCE_FIELDS,
  [CLS.AMAZONS3]: AMAZONS3_FIELDS,
  [CLS.JIRA]: JIRA_FIELDS,
  [CLS.JDBC]: JDBC_FIELDS,
  [CLS.DROPBOX]: DROPBOX_FIELDS,
  [CLS.GOOGLEDRIVE]: GOOGLEDRIVE_FIELDS,
  [CLS.SHAREPOINT]: SHAREPOINT_FIELDS,
  [CLS.EMAIL]: EMAIL_FIELDS,
  [CLS.WEB]: WEB_CRAWLER_FIELDS,
};

/* ──────────────────────── CMIS vendor presets ──────────────────────── */

const CMIS_VENDOR_PRESETS: Record<string, { label: string; values: Record<string, string> }> = {
  alfresco: {
    label: 'Alfresco',
    values: {
      protocol: 'https', port: '443',
      path: '/alfresco/api/-default-/cmis/versions/1.1/atom',
      binding: 'atom', repositoryId: '-default-', cmisVendor: 'alfresco', maxFileSize: '0',
    },
  },
  sharepoint: {
    label: 'SharePoint (CMIS)',
    values: {
      protocol: 'https', port: '443',
      path: '/_vti_bin/cmis/rest?getRepositories',
      binding: 'atom', repositoryId: '', cmisVendor: 'other', maxFileSize: '0',
    },
  },
  filenet: {
    label: 'IBM FileNet',
    values: {
      protocol: 'https', port: '9443',
      path: '/openfncmis_wlp/services/cmis',
      binding: 'atom', repositoryId: '', cmisVendor: 'other', maxFileSize: '0',
    },
  },
  nuxeo: {
    label: 'Nuxeo',
    values: {
      protocol: 'https', port: '8080',
      path: '/nuxeo/atom/cmis',
      binding: 'atom', repositoryId: 'default', cmisVendor: 'other', maxFileSize: '0',
    },
  },
  other: {
    label: 'Other CMIS Server',
    values: {
      protocol: 'https', port: '443', path: '', binding: 'atom',
      repositoryId: '', cmisVendor: 'other', maxFileSize: '0',
    },
  },
};

/* ──────────────────────── REST API vendor presets ──────────────────────── */

const REST_API_VENDOR_PRESETS: Record<string, { label: string; values: Record<string, string> }> = {
  confluence: {
    label: 'Confluence (Atlassian)',
    values: {
      VENDOR: 'confluence', AUTHTYPE: 'basic', PROTOCOL: 'https', PORT: '443',
      BASEPATH: '/wiki/api/v2', SEEDENDPOINT: '/pages',
      DOCENDPOINT: '/pages/{id}?body-format=storage',
      ACLENDPOINT: '/pages/{id}/operations',
      PAGINATIONTYPE: 'cursor', PAGESIZE: '100',
      CURSORFIELD: '$._links.next', CURSORPARAM: 'cursor',
      ITEMSPATH: '$.results', IDFIELD: '$.id', TITLEFIELD: '$.title',
      CONTENTFIELD: '$.body.storage.value',
      CREATEDDATEFIELD: '$.createdAt', MODIFIEDDATEFIELD: '$.version.createdAt',
    },
  },
  jira: {
    label: 'Jira (Atlassian)',
    values: {
      VENDOR: 'jira', AUTHTYPE: 'basic', PROTOCOL: 'https', PORT: '443',
      BASEPATH: '/rest/api/3', SEEDENDPOINT: '/search', DOCENDPOINT: '/issue/{id}',
      PAGINATIONTYPE: 'offset', PAGESIZE: '100',
      ITEMSPATH: '$.issues', IDFIELD: '$.id', TITLEFIELD: '$.fields.summary',
      CONTENTFIELD: '$.fields.description',
      CREATEDDATEFIELD: '$.fields.created', MODIFIEDDATEFIELD: '$.fields.updated',
    },
  },
  wordpress: {
    label: 'WordPress',
    values: {
      VENDOR: 'wordpress', AUTHTYPE: 'basic', PROTOCOL: 'https', PORT: '443',
      BASEPATH: '/wp-json/wp/v2', SEEDENDPOINT: '/posts', DOCENDPOINT: '/posts/{id}',
      PAGINATIONTYPE: 'page', PAGESIZE: '100',
      ITEMSPATH: '$', IDFIELD: '$.id', TITLEFIELD: '$.title.rendered',
      CONTENTFIELD: '$.content.rendered',
      CREATEDDATEFIELD: '$.date_gmt', MODIFIEDDATEFIELD: '$.modified_gmt',
    },
  },
  github: {
    label: 'GitHub',
    values: {
      VENDOR: 'github', AUTHTYPE: 'bearer', PROTOCOL: 'https',
      SERVER: 'api.github.com', PORT: '443',
      SEEDENDPOINT: '/repos/{owner}/{repo}/contents',
      DOCENDPOINT: '/repos/{owner}/{repo}/contents/{path}',
      ACLENDPOINT: '/repos/{owner}/{repo}/collaborators',
      PAGINATIONTYPE: 'page', PAGESIZE: '100',
      ITEMSPATH: '$', IDFIELD: '$.sha', TITLEFIELD: '$.name',
      CONTENTFIELD: '$.content', SIZEFIELD: '$.size',
    },
  },
  sharepoint: {
    label: 'SharePoint Online',
    values: {
      VENDOR: 'sharepoint', AUTHTYPE: 'bearer', PROTOCOL: 'https', PORT: '443',
      BASEPATH: '/_api',
      SEEDENDPOINT: "/web/lists/getbytitle('Documents')/items",
      DOCENDPOINT: "/web/lists/getbytitle('Documents')/items({id})",
      CONTENTENDPOINT: "/web/GetFileByServerRelativeUrl('{path}')/$value",
      ACLENDPOINT: "/web/lists/getbytitle('Documents')/items({id})/roleassignments",
      PAGINATIONTYPE: 'link', PAGESIZE: '5000', CURSORFIELD: '$.d.__next',
      ITEMSPATH: '$.d.results', IDFIELD: '$.Id', TITLEFIELD: '$.Title',
      CREATEDDATEFIELD: '$.Created', MODIFIEDDATEFIELD: '$.Modified',
      SIZEFIELD: '$.File.Length',
    },
  },
  notion: {
    label: 'Notion',
    values: {
      VENDOR: 'notion', AUTHTYPE: 'bearer', PROTOCOL: 'https',
      SERVER: 'api.notion.com', PORT: '443', BASEPATH: '/v1',
      SEEDENDPOINT: '/search', DOCENDPOINT: '/pages/{id}',
      CONTENTENDPOINT: '/blocks/{id}/children',
      PAGINATIONTYPE: 'cursor', PAGESIZE: '100',
      CURSORFIELD: '$.next_cursor', CURSORPARAM: 'start_cursor',
      ITEMSPATH: '$.results', IDFIELD: '$.id',
      TITLEFIELD: '$.properties.title.title[0].plain_text',
      CREATEDDATEFIELD: '$.created_time', MODIFIEDDATEFIELD: '$.last_edited_time',
    },
  },
  drupal: {
    label: 'Drupal',
    values: {
      VENDOR: 'drupal', AUTHTYPE: 'basic', PROTOCOL: 'https', PORT: '443',
      BASEPATH: '/jsonapi', SEEDENDPOINT: '/node/article', DOCENDPOINT: '/node/article/{id}',
      PAGINATIONTYPE: 'cursor', PAGESIZE: '50',
      CURSORFIELD: '$.links.next.href',
      ITEMSPATH: '$.data', IDFIELD: '$.id', TITLEFIELD: '$.attributes.title',
      CONTENTFIELD: '$.attributes.body.value',
      CREATEDDATEFIELD: '$.attributes.created', MODIFIEDDATEFIELD: '$.attributes.changed',
    },
  },
  alfresco: {
    label: 'Alfresco (REST API)',
    values: {
      VENDOR: 'alfresco', AUTHTYPE: 'basic', PROTOCOL: 'https', PORT: '443',
      BASEPATH: '/alfresco/api/-default-/public/alfresco/versions/1',
      SEEDENDPOINT: '/queries/nodes?term=*', DOCENDPOINT: '/nodes/{id}',
      CONTENTENDPOINT: '/nodes/{id}/content', ACLENDPOINT: '/nodes/{id}/permissions',
      PAGINATIONTYPE: 'offset', PAGESIZE: '100',
      ITEMSPATH: '$.list.entries', IDFIELD: '$.entry.id', TITLEFIELD: '$.entry.name',
      MIMETYPEFIELD: '$.entry.content.mimeType',
      CREATEDDATEFIELD: '$.entry.createdAt', MODIFIEDDATEFIELD: '$.entry.modifiedAt',
      SIZEFIELD: '$.entry.content.sizeInBytes',
    },
  },
  other: {
    label: 'Other (Custom)',
    values: {
      VENDOR: 'other', AUTHTYPE: 'none', PROTOCOL: 'https', PORT: '443',
      PAGINATIONTYPE: 'offset', PAGESIZE: '100',
      ITEMSPATH: '$.results', IDFIELD: '$.id', TITLEFIELD: '$.name',
    },
  },
};

/* ──────────────────────── ACL Group API vendor presets (from ManifoldCF editConfiguration.js) ──────────────────────── */

const ACL_GROUP_API_PRESETS: Record<string, { groupApiUrl: string; groupMembersApiUrl: string }> = {
  alfresco: {
    groupApiUrl: '/alfresco/api/-default-/public/alfresco/versions/1/groups',
    groupMembersApiUrl: '/alfresco/api/-default-/public/alfresco/versions/1/groups/{groupId}/members',
  },
  sharepoint: {
    groupApiUrl: '/_api/web/sitegroups',
    groupMembersApiUrl: '/_api/web/sitegroups({groupId})/users',
  },
  filenet: {
    groupApiUrl: '/P8CE/rest/v1/groups',
    groupMembersApiUrl: '/P8CE/rest/v1/groups/{groupId}/members',
  },
  opentext: {
    groupApiUrl: '/dctm-rest/repositories/default/groups',
    groupMembersApiUrl: '/dctm-rest/repositories/default/groups/{groupId}/members',
  },
  nuxeo: {
    groupApiUrl: '/nuxeo/api/v1/directory/groupDirectory',
    groupMembersApiUrl: '/nuxeo/api/v1/group/{groupId}',
  },
  // REST API vendors
  confluence: {
    groupApiUrl: '/wiki/rest/api/group',
    groupMembersApiUrl: '/wiki/rest/api/group/{groupId}/member',
  },
  jira: {
    groupApiUrl: '/rest/api/3/group/bulk',
    groupMembersApiUrl: '/rest/api/3/group/member?groupId={groupId}',
  },
  github: {
    groupApiUrl: '/orgs/{org}/teams',
    groupMembersApiUrl: '/orgs/{org}/teams/{groupId}/members',
  },
};

/** Returns the vendor preset map if the connector class has sub-vendor presets */
function getVendorPresets(
  connectorClass: string,
): Record<string, { label: string; values: Record<string, string> }> | null {
  if (connectorClass === CLS.CMIS) return CMIS_VENDOR_PRESETS;
  if (connectorClass === CLS.RESTAPI) return REST_API_VENDOR_PRESETS;
  return null;
}

/** Returns whether the connector supports ACL enforcement (group/user API) */
function supportsAclEnforcement(connectorClass: string | undefined): boolean {
  return connectorClass === CLS.CMIS || connectorClass === CLS.RESTAPI;
}

/** Returns pre-populated ACL group API URLs for a given vendor */
function getAclGroupApiDefaults(vendorId: string): { groupApiUrl: string; groupMembersApiUrl: string } {
  return ACL_GROUP_API_PRESETS[vendorId] || { groupApiUrl: '', groupMembersApiUrl: '' };
}

/* ──────────────────────── Helper: build initial config values from field defs ──────────────────────── */

function buildInitialValues(fields: FieldDef[]): Record<string, string> {
  const vals: Record<string, string> = {};
  fields.forEach((f) => { vals[f.key] = f.defaultValue; });
  return vals;
}

/* ──────────────────────── Field renderer ──────────────────────── */

const FieldRow: React.FC<{
  field: FieldDef;
  value: string;
  onChange: (key: string, val: string) => void;
}> = ({ field, value, onChange }) => {
  const label = (
    <label style={{ display: 'block', marginBottom: 4, fontWeight: 500, fontSize: 13 }}>
      {field.label}
      {field.required && <span style={{ color: '#ff4d4f', marginLeft: 2 }}>*</span>}
    </label>
  );

  let control: React.ReactNode;
  switch (field.type) {
    case 'select':
      control = (
        <Select
          style={{ width: '100%' }}
          value={value || undefined}
          onChange={(v) => onChange(field.key, v)}
          placeholder={field.placeholder}
          options={field.options}
          allowClear
        />
      );
      break;
    case 'switch':
      control = (
        <Switch
          checked={value === 'true'}
          onChange={(checked) => onChange(field.key, String(checked))}
        />
      );
      break;
    case 'textarea':
      control = (
        <Input.TextArea
          value={value}
          onChange={(e) => onChange(field.key, e.target.value)}
          placeholder={field.placeholder}
          rows={3}
        />
      );
      break;
    case 'number':
      control = (
        <Input
          type="number"
          value={value}
          onChange={(e) => onChange(field.key, e.target.value)}
          placeholder={field.placeholder}
        />
      );
      break;
    case 'password':
      control = (
        <Input.Password
          value={value}
          onChange={(e) => onChange(field.key, e.target.value)}
          placeholder={field.placeholder}
        />
      );
      break;
    default:
      control = (
        <Input
          value={value}
          onChange={(e) => onChange(field.key, e.target.value)}
          placeholder={field.placeholder}
        />
      );
  }

  return (
    <div style={{ marginBottom: 14 }}>
      {label}
      {control}
    </div>
  );
};

/* ──────────────────────── Group fields into main + collapsible sections ──────────────────────── */

function groupFields(fields: FieldDef[]): {
  main: FieldDef[];
  groups: { title: string; fields: FieldDef[] }[];
} {
  const main: FieldDef[] = [];
  const groupMap = new Map<string, FieldDef[]>();
  for (const f of fields) {
    if (f.group) {
      if (!groupMap.has(f.group)) groupMap.set(f.group, []);
      groupMap.get(f.group)!.push(f);
    } else {
      main.push(f);
    }
  }
  const groups = Array.from(groupMap.entries()).map(([title, gFields]) => ({
    title,
    fields: gFields,
  }));
  return { main, groups };
}

/* ──────────────────────── Main component ──────────────────────── */

export default function SourceCreate() {
  const params = useParams<{ id?: string }>();
  const isEdit = !!params.id;

  const [state, setState] = useSetState<FormState>({
    step: 0,
    name: '',
    description: '',
    selectedConnector: null,
    selectedVendor: null,
    configValues: {},
    extraEntries: [],
    connectorTypes: [],
    loading: false,
    testResult: null,
    testPassed: false,
    createdSourceId: null,
    enforceAcl: false,
    aclGroupApiUrl: '',
    aclGroupMembersApiUrl: '',
    aclTestResult: null,
    aclTestPassed: false,
    queryTestLoading: false,
    activeTestType: null,
    queryTestResult: null,
    queryTestItems: [],
    queryTestCount: 0,

    editDataLoaded: false,
  });

  const [countdown, setCountdown] = useState(0);

  useMount(() => {
    getConnectorTypes()
      .then((types) => {
        const typedTypes = (types || []) as unknown as ConnectorType[];
        setState({ connectorTypes: typedTypes });

        // If editing, load existing source data
        if (isEdit && params.id) {
          getSourceDetail(params.id).then((detail) => {
            const matchedConnector = typedTypes.find(
              (ct) => ct.class_name === detail.connector_class
            ) || { description: detail.connector_type, class_name: detail.connector_class } as ConnectorType;

            const config = detail.connection_config || {};
            // Convert maxFileSize from bytes (stored) to KB (UI display)
            if (config.maxFileSize && Number(config.maxFileSize) > 0) {
              config.maxFileSize = String(Math.round(Number(config.maxFileSize) / 1024));
            }
            if (config.MAXFILESIZE && Number(config.MAXFILESIZE) > 0) {
              config.MAXFILESIZE = String(Math.round(Number(config.MAXFILESIZE) / 1024));
            }
            // Detect vendor from config
            let detectedVendor: string | null = null;
            if (matchedConnector.class_name === CLS.CMIS) {
              // Try to detect from cmisVendor param
              detectedVendor = config.cmisVendor === 'alfresco' ? 'alfresco' : 'other';
            } else if (matchedConnector.class_name === CLS.RESTAPI) {
              detectedVendor = config.VENDOR || 'other';
            }

            setState({
              selectedConnector: matchedConnector,
              selectedVendor: detectedVendor,
              name: detail.name,
              description: detail.description || '',
              configValues: config,
              createdSourceId: detail.source_id,
              enforceAcl: !!(config.groupApiUrl || config.ACLENDPOINT),
              aclGroupApiUrl: config.groupApiUrl || '',
              aclGroupMembersApiUrl: config.groupMembersApiUrl || '',

              editDataLoaded: true,
              step: 1, // Go directly to config step for edit
            });
          }).catch(() => {
            message.error('Failed to load source details');
          });
        }
      })
      .catch(() => {
        message.error('Failed to load connector types');
      });
  });

  /* ── handlers ── */

  const handleCancel = () => {
    AlertDialog.warning({
      title: (
        <span className={styles['confirm-title']}>
          {$i18n.get({ id: 'main.pages.Source.Create.index.confirmDiscard', dm: 'Discard source creation?' })}
        </span>
      ),
      content: (
        <span className={styles['confirm-content']}>
          {$i18n.get({ id: 'main.pages.Source.Create.index.discardWarning', dm: 'Data will not be saved.' })}
        </span>
      ),
      okText: $i18n.get({ id: 'main.pages.Source.Create.index.confirmDiscardBtn', dm: 'Discard' }),
      cancelText: $i18n.get({ id: 'main.pages.Source.Create.index.continueEditing', dm: 'Continue Editing' }),
      onOk: () => history.push('/source'),
    });
  };

  const selectConnector = (ct: ConnectorType) => {
    const presets = getVendorPresets(ct.class_name);
    const fields = CONNECTOR_FIELDS[ct.class_name];
    if (presets) {
      // Has vendor sub-presets — show vendor picker, don't pre-fill yet
      setState({
        selectedConnector: ct,
        selectedVendor: null,
        configValues: fields ? buildInitialValues(fields) : {},
        extraEntries: [],
        testResult: null,
        testPassed: false,
      });
    } else {
      setState({
        selectedConnector: ct,
        selectedVendor: null,
        configValues: fields ? buildInitialValues(fields) : {},
        extraEntries: fields ? [] : [{ key: '', value: '' }],
        testResult: null,
        testPassed: false,
      });
    }
  };

  const selectVendor = (vendorId: string) => {
    if (!state.selectedConnector) return;
    const presets = getVendorPresets(state.selectedConnector.class_name);
    if (!presets || !presets[vendorId]) return;
    const fields = CONNECTOR_FIELDS[state.selectedConnector.class_name];
    const base = fields ? buildInitialValues(fields) : {};
    // Overlay vendor-specific values onto defaults
    const merged = { ...base, ...presets[vendorId].values };
    // Auto-set cmisVendor param for CMIS connectors (removed from UI but still needed by ManifoldCF)
    if (state.selectedConnector.class_name === CLS.CMIS) {
      merged.cmisVendor = (vendorId === 'alfresco') ? 'alfresco' : 'other';
    }
    // Pre-populate ACL group API URLs from vendor defaults
    const aclDefaults = getAclGroupApiDefaults(vendorId);
    setState({
      selectedVendor: vendorId,
      configValues: merged,
      testResult: null,
      testPassed: false,
      aclGroupApiUrl: aclDefaults.groupApiUrl,
      aclGroupMembersApiUrl: aclDefaults.groupMembersApiUrl,
      aclTestResult: null,
      aclTestPassed: false,
    });
  };

  const updateConfigValue = (key: string, val: string) => {
    setState({ configValues: { ...state.configValues, [key]: val } });
  };

  const addExtraEntry = () => {
    setState({ extraEntries: [...state.extraEntries, { key: '', value: '' }] });
  };

  const removeExtraEntry = (index: number) => {
    const next = state.extraEntries.filter((_, i) => i !== index);
    setState({ extraEntries: next });
  };

  const updateExtraEntry = (index: number, field: 'key' | 'value', val: string) => {
    const next = [...state.extraEntries];
    next[index] = { ...next[index], [field]: val };
    setState({ extraEntries: next });
  };

  /** Build final config map from typed fields + extra entries + ACL config */
  const buildConfigMap = (): Record<string, any> => {
    const map: Record<string, any> = {};
    // Typed fields
    Object.entries(state.configValues).forEach(([k, v]) => {
      if (v !== undefined && v !== '') {
        // Convert maxFileSize from KB (UI) to bytes (backend)
        if ((k === 'maxFileSize' || k === 'MAXFILESIZE') && v && Number(v) > 0) {
          map[k] = String(Number(v) * 1024);
        } else {
          map[k] = v;
        }
      }
    });
    // ACL enforcement fields (CMIS connector uses groupApiUrl/groupMembersApiUrl params)
    if (state.enforceAcl && state.selectedConnector) {
      if (state.selectedConnector.class_name === CLS.CMIS) {
        if (state.aclGroupApiUrl) map.groupApiUrl = state.aclGroupApiUrl;
        if (state.aclGroupMembersApiUrl) map.groupMembersApiUrl = state.aclGroupMembersApiUrl;
      }
      // REST API connector already has ACLENDPOINT in configValues
    }
    // Extra key/value entries
    state.extraEntries.forEach((e) => {
      if (e.key.trim()) {
        map[e.key.trim()] = e.value;
      }
    });
    return map;
  };

  const validateStep = (): string | null => {
    if (state.step === 0) {
      if (!state.selectedConnector) return 'Please select a connector type';
      const presets = getVendorPresets(state.selectedConnector.class_name);
      if (presets && !state.selectedVendor) return 'Please select a vendor preset';
    }
    if (state.step === 1) {
      if (!state.name.trim()) return 'Please enter a name';
      // Check required fields
      const fields = CONNECTOR_FIELDS[state.selectedConnector?.class_name || ''];
      if (fields) {
        for (const f of fields) {
          if (f.required && !state.configValues[f.key]?.trim()) {
            return `Please fill in required field: ${f.label}`;
          }
        }
      }
      const config = buildConfigMap();
      if (Object.keys(config).length === 0) return 'Please add at least one configuration parameter';
    }
    return null;
  };

  const handleNext = () => {
    const err = validateStep();
    if (err) { message.warning(err); return; }
    // Reset test results when moving TO the test step so stale data is cleared
    if (state.step === 1) {
      setState({
        step: state.step + 1,
        testResult: null,
        testPassed: false,
        aclTestResult: null,
        aclTestPassed: false,
        queryTestResult: null,
        queryTestItems: [],
        queryTestCount: 0,
      });
    } else {
      setState({ step: state.step + 1 });
    }
  };

  const handlePrev = () => {
    // Reset test results when leaving the test step so updated config gets fresh results
    if (state.step === 2) {
      setState({
        step: state.step - 1,
        testResult: null,
        testPassed: false,
        aclTestResult: null,
        aclTestPassed: false,
        queryTestResult: null,
        queryTestItems: [],
        queryTestCount: 0,
      });
    } else {
      setState({ step: state.step - 1 });
    }
  };

  const handleCreate = () => {
    const err = validateStep();
    if (err) { message.warning(err); return; }
    setState({ loading: true });

    const payload = {
      name: state.name.trim(),
      description: state.description.trim(),
      connector_type: state.selectedConnector!.description,
      connector_class: state.selectedConnector!.class_name,
      connection_config: buildConfigMap(),
    };

    const savePromise = (isEdit && state.createdSourceId)
      ? updateSource({ source_id: state.createdSourceId, ...payload }).then(() => state.createdSourceId!)
      : createSource(payload);

    savePromise
      .then((sourceId) => {
        setState({ loading: false, createdSourceId: sourceId });
        message.success('Source saved as draft');
        let c = 3;
        setCountdown(c);
        const interval = setInterval(() => {
          c -= 1;
          setCountdown(c);
          if (c <= 0) { clearInterval(interval); history.push('/source'); }
        }, 1000);
      })
      .catch(() => {
        setState({ loading: false });
        message.error('Failed to save source');
      });
  };

  /** Save as draft — usable from any step (saves current state without validation) */
  const handleSaveAsDraft = () => {
    if (!state.name.trim()) {
      message.warning('Please enter a name before saving');
      return;
    }
    if (!state.selectedConnector) {
      message.warning('Please select a connector type before saving');
      return;
    }
    setState({ loading: true });

    const payload = {
      name: state.name.trim(),
      description: state.description.trim(),
      connector_type: state.selectedConnector.description,
      connector_class: state.selectedConnector.class_name,
      connection_config: buildConfigMap(),
    };

    const savePromise = state.createdSourceId
      ? updateSource({ source_id: state.createdSourceId, ...payload }).then(() => state.createdSourceId!)
      : createSource(payload);

    savePromise
      .then((sourceId) => {
        setState({ loading: false, createdSourceId: sourceId });
        message.success('Saved as draft');
      })
      .catch(() => {
        setState({ loading: false });
        message.error('Failed to save draft');
      });
  };

  /** Test connection only (no enable) */
  const handleTest = () => {
    setState({ loading: true, testResult: null, testPassed: false, aclTestResult: null, aclTestPassed: false });

    const ensureSourceSaved = state.createdSourceId
      ? updateSource({
          source_id: state.createdSourceId,
          name: state.name.trim(),
          description: state.description.trim(),
          connection_config: buildConfigMap(),
        }).then(() => state.createdSourceId!)
      : createSource({
          name: state.name.trim(),
          description: state.description.trim(),
          connector_type: state.selectedConnector!.description,
          connector_class: state.selectedConnector!.class_name,
          connection_config: buildConfigMap(),
        }).then((sourceId) => {
          setState({ createdSourceId: sourceId });
          return sourceId;
        });

    ensureSourceSaved
      .then((sourceId) => {
        return testConnection(sourceId!).then((connResult) => {
          const connPassed = connResult.result?.includes('Connection working') ?? false;
          setState({
            loading: false,
            testResult: connResult.result || (connPassed ? 'Connection working' : 'Connection test failed'),
            testPassed: connPassed,
          });
        });
      })
      .catch(() => {
        setState({
          loading: false,
          testResult: 'Test failed — could not reach the source',
          testPassed: false,
        });
      });
  };

  /** Test a specific query (CMIS query, REST API seed, Group API, User API) */
  const handleTestQuery = (testType: string, queryOverride?: string) => {
    if (!state.createdSourceId) {
      // Need to save first
      setState({ queryTestLoading: true, activeTestType: testType });
      createSource({
        name: state.name.trim() || 'Untitled Source',
        description: state.description.trim(),
        connector_type: state.selectedConnector!.description,
        connector_class: state.selectedConnector!.class_name,
        connection_config: buildConfigMap(),
      }).then((sourceId) => {
        setState({ createdSourceId: sourceId });
        return testQuery(sourceId, testType, queryOverride);
      }).then((result) => {
        const passed = result.status === 'ok' || result.status === 'PASS';
        setState({
          queryTestLoading: false,
          activeTestType: null,
          queryTestResult: passed ? 'ok' : (result.message || 'Test failed'),
          queryTestItems: result.items || [],
          queryTestCount: result.count || 0,
        });
      }).catch((err) => {
        setState({
          queryTestLoading: false,
          activeTestType: null,
          queryTestResult: err?.message || 'Test failed',
          queryTestItems: [],
          queryTestCount: 0,
        });
      });
      return;
    }

    // Source already saved — update config then query
    setState({ queryTestLoading: true, activeTestType: testType, queryTestResult: null, queryTestItems: [], queryTestCount: 0 });
    updateSource({
      source_id: state.createdSourceId,
      name: state.name.trim(),
      description: state.description.trim(),
      connection_config: buildConfigMap(),
    }).then(() => {
      return testQuery(state.createdSourceId!, testType, queryOverride);
    }).then((result) => {
      const passed = result.status === 'ok' || result.status === 'PASS';
      setState({
        queryTestLoading: false,
        activeTestType: null,
        queryTestResult: passed ? 'ok' : (result.message || 'Test failed'),
        queryTestItems: result.items || [],
        queryTestCount: result.count || 0,
      });
    }).catch((err) => {
      setState({
        queryTestLoading: false,
        activeTestType: null,
        queryTestResult: err?.message || 'Test failed',
        queryTestItems: [],
        queryTestCount: 0,
      });
    });
  };

  /* ── Render typed config form for current connector ── */

  const renderTypedConfigForm = () => {
    const connClass = state.selectedConnector?.class_name || '';
    const fields = CONNECTOR_FIELDS[connClass];

    if (!fields) {
      // No typed fields — show generic key/value editor
      return (
        <>
          {(state.extraEntries.length === 0 ? [{ key: '', value: '' }] : state.extraEntries).map(
            (entry, idx) => (
              <div key={idx} className={styles['config-entry']}>
                <Input className={styles['config-key']} placeholder="Key" value={entry.key}
                  onChange={(e) => updateExtraEntry(idx, 'key', e.target.value)} />
                <Input className={styles['config-value']} placeholder="Value" value={entry.value}
                  onChange={(e) => updateExtraEntry(idx, 'value', e.target.value)}
                  type={entry.key.toLowerCase().includes('password') ? 'password' : 'text'} />
                <Button className={styles['config-remove']} type="text" danger
                  onClick={() => removeExtraEntry(idx)}>×</Button>
              </div>
            ),
          )}
          <Button type="dashed" onClick={addExtraEntry} style={{ marginTop: 8 }}>
            + Add Parameter
          </Button>
        </>
      );
    }

    const { main, groups } = groupFields(fields);
    return (
      <>
        {/* Main fields */}
        {main.map((f) => (
          <FieldRow key={f.key} field={f} value={state.configValues[f.key] || ''}
            onChange={updateConfigValue} />
        ))}

        {/* Collapsible groups */}
        {groups.length > 0 && (
          <Collapse
            ghost
            style={{ marginTop: 8, marginBottom: 8 }}
            items={groups.map((g, gi) => ({
              key: String(gi),
              label: <span style={{ fontWeight: 500 }}>{g.title}</span>,
              children: g.fields.map((f) => (
                <FieldRow key={f.key} field={f} value={state.configValues[f.key] || ''}
                  onChange={updateConfigValue} />
              )),
            }))}
          />
        )}

        {/* Extra custom entries */}
        {state.extraEntries.length > 0 && (
          <div style={{ marginTop: 16 }}>
            <div style={{ fontWeight: 500, marginBottom: 8 }}>Custom Parameters</div>
            {state.extraEntries.map((entry, idx) => (
              <div key={idx} className={styles['config-entry']}>
                <Input className={styles['config-key']} placeholder="Key" value={entry.key}
                  onChange={(e) => updateExtraEntry(idx, 'key', e.target.value)} />
                <Input className={styles['config-value']} placeholder="Value" value={entry.value}
                  onChange={(e) => updateExtraEntry(idx, 'value', e.target.value)}
                  type={entry.key.toLowerCase().includes('password') ? 'password' : 'text'} />
                <Button className={styles['config-remove']} type="text" danger
                  onClick={() => removeExtraEntry(idx)}>×</Button>
              </div>
            ))}
          </div>
        )}
        <Button type="dashed" onClick={addExtraEntry} style={{ marginTop: 8 }}>
          + Add Custom Parameter
        </Button>

        {/* ── ACL Enforcement Section (CMIS & REST API only) ── */}
        {supportsAclEnforcement(connClass) && (
          <div style={{
            marginTop: 24, padding: 16, borderRadius: 8,
            border: '1px solid var(--ag-ant-color-border)',
            background: state.enforceAcl ? 'var(--ag-ant-color-primary-bg)' : 'transparent',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
              <Switch
                checked={state.enforceAcl}
                onChange={(checked) => setState({
                  enforceAcl: checked,
                  aclTestResult: null,
                  aclTestPassed: false,
                })}
              />
              <span style={{ fontWeight: 600, fontSize: 14 }}>
                {$i18n.get({ id: 'main.pages.Source.Create.index.enforceAcl', dm: 'Enforce ACL (Access Control)' })}
              </span>
            </div>
            <div style={{ fontSize: 12, color: 'var(--ag-ant-color-text-secondary)', marginBottom: 12 }}>
              {$i18n.get({
                id: 'main.pages.Source.Create.index.enforceAclDesc',
                dm: 'When enabled, documents will be indexed with ACL metadata for permission-aware search. Group API URLs are pre-populated based on the selected vendor and are editable.',
              })}
            </div>
            {connClass === CLS.CMIS && (
              <>
                <div style={{ marginBottom: 14 }}>
                  <label style={{ display: 'block', marginBottom: 4, fontWeight: 500, fontSize: 13 }}>
                    {$i18n.get({ id: 'main.pages.Source.Create.index.groupApiUrl', dm: 'Group List API Path' })}
                    {state.enforceAcl && <span style={{ color: '#ff4d4f', marginLeft: 2 }}>*</span>}
                  </label>
                  <Input
                    value={state.aclGroupApiUrl}
                    onChange={(e) => setState({ aclGroupApiUrl: e.target.value })}
                    placeholder="/alfresco/api/-default-/public/alfresco/versions/1/groups"
                  />
                </div>
                <div style={{ marginBottom: 14 }}>
                  <label style={{ display: 'block', marginBottom: 4, fontWeight: 500, fontSize: 13 }}>
                    {$i18n.get({ id: 'main.pages.Source.Create.index.groupMembersApiUrl', dm: 'Group Members API Path' })}
                    {state.enforceAcl && <span style={{ color: '#ff4d4f', marginLeft: 2 }}>*</span>}
                    <span style={{ fontWeight: 400, fontSize: 11, color: 'var(--ag-ant-color-text-tertiary)', marginLeft: 6 }}>
                      {'(use {groupId} as placeholder)'}
                    </span>
                  </label>
                  <Input
                    value={state.aclGroupMembersApiUrl}
                    onChange={(e) => setState({ aclGroupMembersApiUrl: e.target.value })}
                    placeholder="/alfresco/api/-default-/public/alfresco/versions/1/groups/{groupId}/members"
                  />
                </div>
              </>
            )}
            {connClass === CLS.RESTAPI && (
              <div style={{ fontSize: 12, color: 'var(--ag-ant-color-text-secondary)' }}>
                {$i18n.get({
                  id: 'main.pages.Source.Create.index.restApiAclNote',
                  dm: 'For REST API connectors, configure the ACL Endpoint and ACL field mappings in the ACL Mappings section above.',
                })}
              </div>
            )}
          </div>
        )}
      </>
    );
  };

  /* ── Step content renderer ── */

  const renderStepContent = () => {
    if (state.step === 0) {
      const vendorPresets = state.selectedConnector
        ? getVendorPresets(state.selectedConnector.class_name)
        : null;

      return (
        <div className={styles['form-section']}>
          <div className={styles['form-section-title']}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.selectConnector', dm: 'Select Connector Type' })}
          </div>
          <div className={styles['connector-grid']}>
            {state.connectorTypes.map((ct) => (
              <div
                key={ct.class_name}
                className={classNames(styles['connector-card'], {
                  [styles['connector-card-selected']]:
                    state.selectedConnector?.class_name === ct.class_name,
                })}
                onClick={() => selectConnector(ct)}
              >
                <div className={styles['connector-name']}>{ct.description}</div>
                <div className={styles['connector-class']}>
                  {ct.class_name.split('.').pop()}
                </div>
              </div>
            ))}
          </div>

          {/* Vendor sub-presets for CMIS / REST API */}
          {vendorPresets && (
            <>
              <div className={styles['form-section-title']} style={{ marginTop: 24 }}>
                {state.selectedConnector?.class_name === CLS.CMIS
                  ? $i18n.get({ id: 'main.pages.Source.Create.index.selectCmisVendor', dm: 'Select CMIS Vendor' })
                  : $i18n.get({ id: 'main.pages.Source.Create.index.selectRestVendor', dm: 'Select REST API Vendor' })}
              </div>
              <div className={styles['connector-grid']}>
                {Object.entries(vendorPresets).map(([vendorId, preset]) => (
                  <div
                    key={vendorId}
                    className={classNames(styles['connector-card'], {
                      [styles['connector-card-selected']]: state.selectedVendor === vendorId,
                    })}
                    onClick={() => selectVendor(vendorId)}
                  >
                    <div className={styles['connector-name']}>{preset.label}</div>
                    <div className={styles['connector-class']}>{vendorId}</div>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      );
    }

    if (state.step === 1) {
      return (
        <div className={styles['form-section']}>
          <div className={styles['form-section-title']}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.sourceDetails', dm: 'Source Details' })}
          </div>
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', marginBottom: 4, fontWeight: 500 }}>
              {$i18n.get({ id: 'main.pages.Source.Create.index.name', dm: 'Name' })}
              <span style={{ color: '#ff4d4f', marginLeft: 2 }}>*</span>
            </label>
            <Input
              value={state.name}
              onChange={(e) => setState({ name: e.target.value })}
              placeholder="e.g. Alfresco Production"
            />
          </div>
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', marginBottom: 4, fontWeight: 500 }}>
              {$i18n.get({ id: 'main.pages.Source.Create.index.description', dm: 'Description' })}
            </label>
            <Input.TextArea
              value={state.description}
              onChange={(e) => setState({ description: e.target.value })}
              placeholder="Optional description"
              rows={2}
            />
          </div>
          <div className={styles['form-section-title']}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.connectionConfig', dm: 'Connection Configuration' })}
            {state.selectedConnector && (
              <span style={{ fontWeight: 400, fontSize: 12, color: 'var(--ag-ant-color-text-tertiary)', marginLeft: 8 }}>
                ({state.selectedConnector.description}
                {state.selectedVendor ? ` — ${state.selectedVendor}` : ''})
              </span>
            )}
          </div>
          {renderTypedConfigForm()}
        </div>
      );
    }

    // Step 2: Test
    const parseAclStatus = (result: string | null): 'PASS' | 'WARN' | 'FAIL' | null => {
      if (!result) return null;
      if (result.startsWith('PASS')) return 'PASS';
      if (result.startsWith('WARN')) return 'WARN';
      return 'FAIL';
    };
    const aclStatus = parseAclStatus(state.aclTestResult);
    const statusColors = {
      PASS: { bg: '#f6ffed', border: '#b7eb8f', text: '#52c41a', label: 'PASS' },
      WARN: { bg: '#fffbe6', border: '#ffe58f', text: '#faad14', label: 'WARNING' },
      FAIL: { bg: '#fff2f0', border: '#ffccc7', text: '#ff4d4f', label: 'FAIL' },
    };

    if (state.step === 2) {
      const connClass = state.selectedConnector?.class_name || '';
      const isCmis = connClass === CLS.CMIS;
      const isRestApi = connClass === CLS.RESTAPI;
      const cmisQuery = state.configValues.cmisQuery || 'SELECT * FROM cmis:document';

      const queryTestColumns = state.queryTestItems.length > 0
        ? Object.keys(state.queryTestItems[0]).map((key) => ({
            title: key,
            dataIndex: key,
            key,
            ellipsis: true,
            width: 180,
            render: (val: any) => {
              const str = typeof val === 'object' ? JSON.stringify(val) : String(val ?? '');
              return str.length > 80 ? str.substring(0, 80) + '...' : str;
            },
          }))
        : [];

      return (
        <div className={styles['form-section']}>
          <div className={styles['form-section-title']}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.testTitle', dm: 'Test Source' })}
          </div>

          {/* Review summary */}
          <div style={{ marginBottom: 12 }}>
            <strong>Name:</strong> {state.name}
            {state.selectedVendor && <span> — <strong>Vendor:</strong> {state.selectedVendor}</span>}
          </div>

          {/* ── Connection Test ── */}
          <div style={{ marginBottom: 20, padding: 16, borderRadius: 8, border: '1px solid var(--ag-ant-color-border)', background: 'var(--ag-ant-color-fill-secondary)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
              <strong style={{ fontSize: 14 }}>Connection Test</strong>
              <Button type="primary" onClick={handleTest} loading={state.loading && !state.queryTestLoading}>
                {$i18n.get({ id: 'main.pages.Source.Create.index.runTest', dm: 'Test Connection' })}
              </Button>
            </div>

            {/* Connection details summary */}
            {(() => {
              const fields = CONNECTOR_FIELDS[connClass];
              if (!fields) return null;
              const displayFields = fields.filter(f => f.type !== 'password' && !f.group && f.key !== 'cmisQuery');
              const vals = state.configValues;
              return (
                <div style={{
                  display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
                  gap: '6px 16px', marginBottom: 12, padding: '10px 12px', borderRadius: 6,
                  background: 'var(--ag-ant-color-bg-layout)', fontSize: 13,
                }}>
                  {displayFields.map(f => (
                    <div key={f.key} style={{ display: 'flex', gap: 6 }}>
                      <span style={{ color: 'var(--ag-ant-color-text-tertiary)', whiteSpace: 'nowrap' }}>{f.label}:</span>
                      <span style={{ fontWeight: 500, wordBreak: 'break-all' }}>{vals[f.key] || f.defaultValue || '—'}</span>
                    </div>
                  ))}
                  {state.enforceAcl && (
                    <div style={{ display: 'flex', gap: 6 }}>
                      <span style={{ color: 'var(--ag-ant-color-text-tertiary)', whiteSpace: 'nowrap' }}>ACL:</span>
                      <span style={{ fontWeight: 500 }}>Enabled</span>
                    </div>
                  )}
                </div>
              );
            })()}

            {state.testResult && (
              <div style={{
                padding: 10, borderRadius: 6,
                background: state.testPassed ? '#f6ffed' : '#fff2f0',
                border: `1px solid ${state.testPassed ? '#b7eb8f' : '#ffccc7'}`,
              }}>
                <Tag color={state.testPassed ? 'success' : 'error'}>{state.testPassed ? 'PASS' : 'FAIL'}</Tag>
                <span style={{ fontSize: 13 }}>{state.testResult}</span>
              </div>
            )}
          </div>

          {/* ── CMIS Query Test ── */}
          {isCmis && (
            <div style={{ marginBottom: 20, padding: 16, borderRadius: 8, border: '1px solid var(--ag-ant-color-border)', background: 'var(--ag-ant-color-fill-secondary)' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                <strong style={{ fontSize: 14 }}>CMIS Query Test</strong>
                <Button
                  onClick={() => handleTestQuery('cmis_query', cmisQuery)}
                  loading={state.queryTestLoading && state.activeTestType === 'cmis_query'}
                  disabled={!state.testPassed || (state.queryTestLoading && state.activeTestType !== 'cmis_query')}
                >
                  Run Query
                </Button>
              </div>
              <div style={{ marginBottom: 8 }}>
                <Input
                  value={state.configValues.cmisQuery || ''}
                  onChange={(e) => updateConfigValue('cmisQuery', e.target.value)}
                  placeholder="SELECT * FROM cmis:document"
                  addonBefore="CMIS Query"
                />
              </div>
              {!state.testPassed && (
                <div style={{ fontSize: 12, color: 'var(--ag-ant-color-text-tertiary)' }}>
                  Run Connection Test first before testing queries.
                </div>
              )}
            </div>
          )}

          {/* ── REST API Seed Test ── */}
          {isRestApi && (
            <div style={{ marginBottom: 20, padding: 16, borderRadius: 8, border: '1px solid var(--ag-ant-color-border)', background: 'var(--ag-ant-color-fill-secondary)' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                <strong style={{ fontSize: 14 }}>REST API Seed Test</strong>
                <Button
                  onClick={() => handleTestQuery('rest_seed')}
                  loading={state.queryTestLoading && state.activeTestType === 'rest_seed'}
                  disabled={!state.testPassed || (state.queryTestLoading && state.activeTestType !== 'rest_seed')}
                >
                  Test Seed Endpoint
                </Button>
              </div>
              <div style={{ fontSize: 12, color: 'var(--ag-ant-color-text-secondary)', marginBottom: 4 }}>
                Tests the Seed/List endpoint: <code>{state.configValues.SEEDENDPOINT || '(not set)'}</code>
              </div>
              {!state.testPassed && (
                <div style={{ fontSize: 12, color: 'var(--ag-ant-color-text-tertiary)' }}>
                  Run Connection Test first before testing the seed endpoint.
                </div>
              )}
            </div>
          )}

          {/* ── Group/User API Test (applies to both CMIS and REST API with ACL) ── */}
          {state.enforceAcl && supportsAclEnforcement(connClass) && (
            <div style={{ marginBottom: 20, padding: 16, borderRadius: 8, border: '1px solid var(--ag-ant-color-border)', background: 'var(--ag-ant-color-fill-secondary)' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                <strong style={{ fontSize: 14 }}>Group / User API Test</strong>
                <div style={{ display: 'flex', gap: 8 }}>
                  <Button
                    onClick={() => handleTestQuery('group_api')}
                    loading={state.queryTestLoading && state.activeTestType === 'group_api'}
                    disabled={!state.testPassed || (state.queryTestLoading && state.activeTestType !== 'group_api')}
                  >
                    Test Group API
                  </Button>
                  <Button
                    onClick={() => handleTestQuery('user_api')}
                    loading={state.queryTestLoading && state.activeTestType === 'user_api'}
                    disabled={!state.testPassed || (state.queryTestLoading && state.activeTestType !== 'user_api')}
                  >
                    Test User API
                  </Button>
                </div>
              </div>
              {isCmis && (
                <div style={{ fontSize: 12, color: 'var(--ag-ant-color-text-secondary)', marginBottom: 4 }}>
                  Group API: <code>{state.aclGroupApiUrl || '(not set)'}</code><br />
                  Group Members API: <code>{state.aclGroupMembersApiUrl || '(not set)'}</code>
                </div>
              )}
              {/* ACL validation result */}
              {state.aclTestResult && aclStatus && (
                <div style={{
                  padding: 10, borderRadius: 6, marginTop: 8,
                  background: statusColors[aclStatus].bg,
                  border: `1px solid ${statusColors[aclStatus].border}`,
                }}>
                  <Tag color={aclStatus === 'PASS' ? 'success' : aclStatus === 'WARN' ? 'warning' : 'error'}>
                    {statusColors[aclStatus].label}
                  </Tag>
                  <span style={{ fontSize: 13 }}>{state.aclTestResult.replace(/^(PASS|WARN|FAIL)\|/, '')}</span>
                </div>
              )}
            </div>
          )}

          {/* ── Query Test Results Table ── */}
          {(state.queryTestResult || state.queryTestItems.length > 0) && (
            <div style={{ marginBottom: 20, padding: 16, borderRadius: 8, border: '1px solid var(--ag-ant-color-border)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                <Tag color={state.queryTestResult === 'ok' ? 'success' : 'error'}>
                  {state.queryTestResult === 'ok' ? 'SUCCESS' : 'ERROR'}
                </Tag>
                <strong>Query Results</strong>
                {state.queryTestCount > 0 && (
                  <span style={{ color: 'var(--ag-ant-color-text-secondary)', fontSize: 13 }}>
                    — {state.queryTestCount} item{state.queryTestCount !== 1 ? 's' : ''} found
                    {state.queryTestItems.length < state.queryTestCount ? ` (showing first ${state.queryTestItems.length})` : ''}
                  </span>
                )}
              </div>
              {state.queryTestResult !== 'ok' && state.queryTestResult && (
                <div style={{ color: '#ff4d4f', fontSize: 13, marginBottom: 8 }}>
                  {state.queryTestResult}
                </div>
              )}
              {state.queryTestItems.length > 0 && (
                <Table
                  dataSource={state.queryTestItems.map((item, idx) => ({ ...item, _key: idx }))}
                  columns={queryTestColumns}
                  rowKey="_key"
                  pagination={false}
                  size="small"
                  scroll={{ x: 'max-content' }}
                  style={{ marginTop: 8 }}
                />
              )}
            </div>
          )}
        </div>
      );
    }

    return null;
  };

  /* ── Footer buttons ── */

  const bottomButton = () => (
    <div className={styles['footer']}>
      {state.step === 0 && (
        <div className={styles['btn-group']}>
          <Button type="primary" onClick={handleNext}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.next', dm: 'Next' })}
          </Button>
          <Button onClick={handleCancel}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.cancel', dm: 'Cancel' })}
          </Button>
        </div>
      )}
      {state.step === 1 && (
        <div className={styles['btn-group']}>
          <Button onClick={handlePrev}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.previous', dm: 'Previous' })}
          </Button>
          <Button type="primary" onClick={handleNext}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.next', dm: 'Next' })}
          </Button>
          <Button onClick={handleSaveAsDraft} loading={state.loading}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.saveDraft', dm: 'Save as Draft' })}
          </Button>
          <Button onClick={handleCancel}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.cancel', dm: 'Cancel' })}
          </Button>
        </div>
      )}
      {state.step === 2 && (
        <div className={styles['btn-group']}>
          <Button onClick={handlePrev}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.previous', dm: 'Previous' })}
          </Button>
          <Button type="primary" onClick={handleCreate} loading={state.loading}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.saveSource', dm: 'Save Source' })}
          </Button>
          <Button onClick={handleSaveAsDraft} loading={state.loading}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.saveDraft', dm: 'Save as Draft' })}
          </Button>
          <Button onClick={handleCancel}>
            {$i18n.get({ id: 'main.pages.Source.Create.index.cancel', dm: 'Cancel' })}
          </Button>
        </div>
      )}
    </div>
  );

  /* ── Success state ── */

  if (state.createdSourceId && countdown > 0) {
    return (
      <InnerLayout
        breadcrumbLinks={[
          { title: $i18n.get({ id: 'main.pages.Source.Create.index.sources', dm: 'Sources' }), path: '/source' },
          { title: $i18n.get({ id: 'main.pages.Source.Create.index.createSource', dm: 'Create Source' }) },
        ]}
      >
        <AlertDialog
          type="success"
          title={$i18n.get({ id: 'main.pages.Source.Create.index.sourceCreated', dm: 'Source Created Successfully' })}
          open={true}
          footer={
            <Button type="primary" onClick={() => history.push('/source')}>
              {$i18n.get(
                { id: 'main.pages.Source.Create.index.returnToSources', dm: 'Return to Sources ({var1}s)' },
                { var1: countdown },
              )}
            </Button>
          }
        >
          {$i18n.get({ id: 'main.pages.Source.Create.index.sourceCreatedDesc', dm: 'Your source has been created. You can now start syncing data or configure a schedule.' })}
        </AlertDialog>
      </InnerLayout>
    );
  }

  /* ── Main render ── */

  return (
    <InnerLayout
      breadcrumbLinks={[
        { title: $i18n.get({ id: 'main.pages.Source.Create.index.sources', dm: 'Sources' }), path: '/source' },
        {
          title: isEdit
            ? $i18n.get({ id: 'main.pages.Source.Create.index.editSource', dm: 'Edit Source' })
            : $i18n.get({ id: 'main.pages.Source.Create.index.createSource', dm: 'Create Source' }),
        },
      ]}
      bottom={bottomButton()}
    >
      <div className={styles['container']}>
        <div className={styles['steps']}>
          <Steps
            current={state.step}
            labelPlacement="vertical"
            size="small"
            items={[
              {
                title: <span className={styles['steps-title']}>
                  {$i18n.get({ id: 'main.pages.Source.Create.index.connectorType', dm: 'Connector Type' })}
                </span>,
                status: state.step === 0 ? 'process' : 'finish',
              },
              {
                title: <span className={styles['steps-title']}>
                  {$i18n.get({ id: 'main.pages.Source.Create.index.configuration', dm: 'Configuration' })}
                </span>,
                status: state.step < 1 ? 'wait' : state.step === 1 ? 'process' : 'finish',
              },
              {
                title: <span className={styles['steps-title']}>
                  {$i18n.get({ id: 'main.pages.Source.Create.index.test', dm: 'Test' })}
                </span>,
                status: state.step < 2 ? 'wait' : 'process',
              },
            ]}
          />
        </div>
        <div className={styles['content']}>{renderStepContent()}</div>
      </div>
    </InnerLayout>
  );
}
