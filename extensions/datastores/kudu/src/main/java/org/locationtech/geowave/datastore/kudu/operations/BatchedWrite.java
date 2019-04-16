package org.locationtech.geowave.datastore.kudu.operations;

import org.apache.kudu.client.*;
import org.locationtech.geowave.core.store.entities.GeoWaveRow;
import org.locationtech.geowave.core.store.entities.GeoWaveValue;
import org.locationtech.geowave.datastore.kudu.KuduRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchedWrite {
  private static final Logger LOGGER = LoggerFactory.getLogger(BatchedWrite.class);
  private final KuduOperations operations;
  private final KuduSession session;
  private final String tableName;

  public BatchedWrite(final String tableName, final KuduOperations operations) {
    this.operations = operations;
    this.session = operations.getSession();
    this.tableName = tableName;
  }

  protected boolean setAutoFlushMode() {
    session.setFlushMode(SessionConfiguration.FlushMode.AUTO_FLUSH_BACKGROUND);
    if (session.getFlushMode() != SessionConfiguration.FlushMode.AUTO_FLUSH_BACKGROUND) {
      LOGGER.error("Fail to set session Flush Mode to AUTO_FLUSH_BACKGROUND.");
      return false;
    }
    return true;
  }

  protected boolean setDefaultFlushMode() {
    session.setFlushMode(SessionConfiguration.FlushMode.AUTO_FLUSH_SYNC);
    if (session.getFlushMode() != SessionConfiguration.FlushMode.AUTO_FLUSH_SYNC) {
      LOGGER.error("Fail to set session Flush Mode back to default flush mode.");
      return false;
    }
    return true;
  }

  public void insert(final GeoWaveRow[] rows) {
    if (setAutoFlushMode()) {
      write(rows);
      setDefaultFlushMode();
    } else {
      LOGGER.error("Fail to set batched write flush mode. Start regular write.");
      write(rows);
    }
  }

  private void write(GeoWaveRow[] rows) {
    for (final GeoWaveRow row : rows) {
      write(row);
    }
  }

  private void write(GeoWaveRow row) {
    try {
      KuduTable table = operations.getTable(tableName);
      for (GeoWaveValue value : row.getFieldValues()) {
        KuduRow kuduRow = new KuduRow(row, value);
        Insert insert = table.newInsert();
        kuduRow.populatePartialRow(insert.getRow());
        OperationResponse resp = session.apply(insert);
        if (resp.hasRowError()) {
          LOGGER.error("Encountered error while applying insert: {}", resp.getRowError());
        }
      }
    } catch (KuduException e) {
      LOGGER.error("Encountered error while writing row", e);
    }
  }
}
