/*
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License, version 2 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/gpl-2.0.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 *
 * Copyright 2006 - 2013 Pentaho Corporation.  All rights reserved.
 */

package org.pentaho.platform.engine.services.connection.datasource.dbcp;

import org.apache.commons.pool.ObjectPool;
import org.pentaho.database.model.IDatabaseConnection;
import org.pentaho.platform.api.data.DBDatasourceServiceException;
import org.pentaho.platform.api.data.IDBDatasourceService;
import org.pentaho.platform.api.engine.ICacheManager;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.api.engine.IPentahoSystemListener;
import org.pentaho.platform.api.engine.ObjectFactoryException;
import org.pentaho.platform.api.repository.datasource.DatasourceMgmtServiceException;
import org.pentaho.platform.api.repository.datasource.IDatasourceMgmtService;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.engine.services.messages.Messages;
import org.pentaho.platform.util.logging.Logger;

import javax.sql.DataSource;
import java.util.List;

public class PooledDatasourceSystemListener implements IPentahoSystemListener {
  public static final String DATASOURCE_REGION = "DATASOURCE"; //$NON-NLS-1$

  public boolean startup( final IPentahoSession session ) {
    try {
      ICacheManager cacheManager = PentahoSystem.getCacheManager( null );
      Logger.debug( this, "PooledDatasourceSystemListener: called for startup" ); //$NON-NLS-1$
      IDatasourceMgmtService datasourceMgmtSvc =
          (IDatasourceMgmtService) PentahoSystem.getObjectFactory().get( IDatasourceMgmtService.class, session );
      if ( !cacheManager.cacheEnabled( IDBDatasourceService.JDBC_POOL ) ) {
        cacheManager.addCacheRegion( IDBDatasourceService.JDBC_POOL );
      }
      if ( !cacheManager.cacheEnabled( IDBDatasourceService.JDBC_DATASOURCE ) ) {
        cacheManager.addCacheRegion( IDBDatasourceService.JDBC_DATASOURCE );
      }
      List<IDatabaseConnection> databaseConnections = datasourceMgmtSvc.getDatasources();
      for ( IDatabaseConnection databaseConnection : databaseConnections ) {
        try {
          Logger.debug( this, "  Setting up pooled Data Source - " + databaseConnection ); //$NON-NLS-1$
          final DataSource ds = PooledDatasourceHelper.setupPooledDataSource( databaseConnection );
          Logger.debug( this, "(storing DataSource under key \"" + IDBDatasourceService.JDBC_DATASOURCE //$NON-NLS-1$
              + databaseConnection.getName() + "\")" ); //$NON-NLS-1$
          cacheManager.putInRegionCache( IDBDatasourceService.JDBC_DATASOURCE, databaseConnection.getName(), ds );
        } catch ( DBDatasourceServiceException dse ) {
          // Skip this datasource pooling
          Logger
              .error(
                this,
                Messages
                  .getInstance()
                  .getErrorString(
                    "PooledDatasourceSystemListener.ERROR_0003_UNABLE_TO_POOL_DATASOURCE", databaseConnection.getName(),
                    dse.getMessage() ) ); //$NON-NLS-1$
          continue;
        }
      }
      Logger.debug( this, "PooledDatasourceSystemListener: done with init" ); //$NON-NLS-1$
      return true;
    } catch ( ObjectFactoryException objface ) {
      Logger.error( this, Messages.getInstance().getErrorString(
          "PooledDatasourceSystemListener.ERROR_0001_UNABLE_TO_INSTANTIATE_OBJECT" ), objface ); //$NON-NLS-1$
      return false;
    } catch ( DatasourceMgmtServiceException dmse ) {
      Logger.error( this, Messages.getInstance().getErrorString(
          "PooledDatasourceSystemListener.ERROR_0002_UNABLE_TO_GET_DATASOURCE" ), dmse ); //$NON-NLS-1$
      return false;
    }
  }

  @SuppressWarnings( "unchecked" )
  public void shutdown() {
    ICacheManager cacheManager = PentahoSystem.getCacheManager( null );
    // Extracting pools from the cache
    List<ObjectPool> objectPools = null;
    objectPools = (List<ObjectPool>) cacheManager.getAllValuesFromRegionCache( IDBDatasourceService.JDBC_POOL );

    Logger.debug( this, "PooledDatasourceSystemListener: called for shutdown" ); //$NON-NLS-1$
    // Clearing all pools
    try {
      if ( objectPools != null ) {
        for ( ObjectPool objectPool : objectPools ) {
          if ( null != objectPool ) {
            objectPool.clear();
          }
        }
      }
    } catch ( Throwable ignored ) {
      Logger.error( this, "Failed to clear connection pool: " + ignored.getMessage(), ignored ); //$NON-NLS-1$
    }
    // Cleaning cache for pools and datasources
    cacheManager.removeRegionCache( IDBDatasourceService.JDBC_POOL );
    cacheManager.removeRegionCache( IDBDatasourceService.JDBC_DATASOURCE );

    Logger.debug( this, "PooledDatasourceSystemListener: completed shutdown" ); //$NON-NLS-1$
    return;
  }

}
