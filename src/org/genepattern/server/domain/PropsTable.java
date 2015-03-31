package org.genepattern.server.domain;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.log4j.Logger;
import org.genepattern.server.DbException;
import org.genepattern.server.database.HibernateSessionManager;
import org.hibernate.Query;

/**
 * Annotation based update to the 'Props' class; 
 * This is a replacement for the org.genepattern.server.domain.Props class which was configured in the Props.hbm.xml file.
 * 
 * This newer annotated file avoids problems with some DB integrations because 
 * the PROPS table has a column named 'key' which is a reserved word on some DB systems.
 * 
 * @author pcarr
 *
 */
@Entity
@Table(name="props")
public class PropsTable {
    private static final Logger log = Logger.getLogger(PropsTable.class);
    
    /**
     * Get the value for the given key in the PROPS table.
     * 
     * @param mgr
     * @param key
     * @return
     * @throws DbException
     */
    public static String selectValue(final HibernateSessionManager mgr, final String key) throws DbException {
        PropsTable row=selectRow(mgr, key);
        if (row==null) {
            return "";
        }
        return row.getValue();
    }
    
    public static PropsTable selectRow(final HibernateSessionManager mgr, final String key) {
        final boolean isInTransaction=mgr.isInTransaction();
        try {
            mgr.beginTransaction();
            final String hql="from "+PropsTable.class.getName()+" p where p.key like :key";
            Query query = mgr.getSession().createQuery(hql);  
            query.setString("key", key);
            List<PropsTable> props=query.list();
            if (props==null || props.size()==0) {
                return null;
            }
            else if (props.size() > 1) {
                log.error("More than one row in PROPS table for key='"+key+"'");
            }
            return props.get(0);
        }
        finally {
            if (!isInTransaction) {
                mgr.closeCurrentSession();
            }
        }
    }

    /**
     * Save a key/value pair to the PROPS table.
     * @param key
     * @param value
     * @return
     */
    public static boolean saveProp(final HibernateSessionManager mgr, final String key, final String value) {
        final boolean isInTransaction=mgr.isInTransaction();
        try {
            mgr.beginTransaction();
            PropsTable props=selectRow(mgr, key);
            if (props==null) {
                props=new PropsTable();
                props.setKey(key);
                props.setValue(value);
                mgr.getSession().save(props);
            }
            else {
                props.setValue(value);
                mgr.getSession().update(props);
            } 
            if (!isInTransaction) {
                mgr.commitTransaction();
            }
            return true;
        }
        catch (Throwable t) {
            log.error("Error saving (key,value) to PROPS table in DB, ('"+key+"', '"+value+"')", t);
            mgr.rollbackTransaction();
            return false;
        }
    }
    
    /**
     * Remove an entry from the PROPS table.
     * @param key
     */
    public static void removeProp(final HibernateSessionManager mgr, final String key) {
        final boolean isInTransaction=mgr.isInTransaction();
        try {
            mgr.beginTransaction();
            PropsTable props=new PropsTable();
            props.setKey(key);
            mgr.getSession().delete(props);
            if (!isInTransaction) {
                mgr.commitTransaction();
            }
        }
        catch (Throwable t) {
            log.error("Error deleting value from PROPS table, key='"+key+"'", t);
            mgr.rollbackTransaction();
        }
    }

    @Id
    @Column(name="key")
    private String key;
    @Column
    private String value;
    
    public String getKey() {
        return key;
    }
    public void setKey(final String key) {
        this.key=key;
    }
    
    public String getValue() {
        return this.value;
    }
    public void setValue(final String value) {
        this.value=value;
    }

}
