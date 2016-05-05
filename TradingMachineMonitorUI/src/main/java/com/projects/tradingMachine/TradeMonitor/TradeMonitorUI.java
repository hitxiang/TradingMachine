package com.projects.tradingMachine.TradeMonitor;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.swing.JFrame;
import javax.swing.table.AbstractTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.projects.tradingMachine.services.database.DatabaseProperties;
import com.projects.tradingMachine.services.database.noSql.MongoDBConnection;
import com.projects.tradingMachine.services.database.noSql.MongoDBManager;
import com.projects.tradingMachine.utility.TradingMachineMessageConsumer;
import com.projects.tradingMachine.utility.Utility;
import com.projects.tradingMachine.utility.Utility.DestinationType;
import com.projects.tradingMachine.utility.order.SimpleOrder;

public final class TradeMonitorUI implements MessageListener {
	private static Logger logger = LoggerFactory.getLogger(TradeMonitorUI.class);
	private final TradingMachineMessageConsumer filledOrdersConsumer;
	private final MongoDBManager mongoDBManager;
	private final Comparator<? super SimpleOrder> dateComparator = (c1, c2) -> c1.getFillDate().compareTo(c2.getFillDate());
	private final List<SimpleOrder> orders;
	private final TradeMonitorTablePanel ordersPanel;
	
	public TradeMonitorUI(final Properties p) throws JMSException, FileNotFoundException, IOException {
		mongoDBManager = new MongoDBManager(new MongoDBConnection(new DatabaseProperties(p.getProperty("mongoDB.host"), 
				Integer.valueOf(p.getProperty("mongoDB.port")), p.getProperty("mongoDB.database"))), p.getProperty("mongoDB.collection"));
		filledOrdersConsumer = new TradingMachineMessageConsumer(p.getProperty("activeMQ.url"), 
				p.getProperty("activeMQ.filledOrdersTopic"), DestinationType.Topic, this, "TradeMonitor", null);
		orders = mongoDBManager.getOrders(Optional.ofNullable(null)).stream().
		sorted(dateComparator.reversed()).collect(Collectors.toList());//sorting isn't strictly needed because it'd have been done by the table sorter.
		ordersPanel = new TradeMonitorTablePanel(orders);
		filledOrdersConsumer.start();
	}
	
	public void createUI() throws JMSException, FileNotFoundException, IOException {
		final JFrame frame = new JFrame("Trade Monitor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);        
		ordersPanel.setOpaque(true); 
        frame.setContentPane(ordersPanel);
        frame.addWindowListener(new WindowAdapter(){
            public void windowClosing(WindowEvent e){
            	try {
					mongoDBManager.close();
				} catch (final Exception e1) {
					logger.warn("Unable to close the MongoDB connection.\n"+e1.getMessage());
				}
            	try {
            		filledOrdersConsumer.stop();
				} catch (final Exception e1) {
					logger.warn("Unable to close the topic subscribe.\n"+e1.getMessage());
				}
            }
        }); 
        frame.setLocation(new Point(300, 300));
        frame.setSize(new Dimension(1500, 500));
        frame.setVisible(true);
	}

    @Override
	public void onMessage(final Message message) {
		try {
			orders.add((SimpleOrder)((ObjectMessage)message).getObject());
			final AbstractTableModel tableModel = ((AbstractTableModel)ordersPanel.getOrdersTable().getModel());
			tableModel.fireTableDataChanged();
		} catch (final JMSException e) {
			logger.warn("Failed to get order, due to "+e.getMessage());
		}
	}
    
    public static void main(final String[] args) {        
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
				 new TradeMonitorUI(Utility.getApplicationProperties("tradeMonitor.properties")).createUI();
				} catch (final Exception e) {
					throw new RuntimeException(e);
				}
            }
        });
    }
}