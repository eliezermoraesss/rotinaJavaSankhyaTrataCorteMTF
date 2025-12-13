package br.com.multfer.tratacorte;

import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.bmp.PersistentLocalEntity;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import com.sankhya.util.JdbcUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.CallableStatement;
import java.sql.Connection;

public class ItemListenerMultfer implements EventoProgramavelJava {

    @Override
    public void beforeUpdate(PersistenceEvent event) throws Exception {}

    @Override
    public void afterDelete(PersistenceEvent event) throws Exception {}

    @Override
    public void afterInsert(PersistenceEvent event) throws Exception {}

    @Override
    public void afterUpdate(PersistenceEvent event) throws Exception {}

    @Override
    public void beforeCommit(TransactionContext ctx) throws Exception {}

    @Override
    public void beforeDelete(PersistenceEvent event) throws Exception {}

    @Override
    public void beforeInsert(PersistenceEvent event) throws Exception {
        EntityFacade dwf = EntityFacadeFactory.getDWFFacade();
        JdbcWrapper jdbc = dwf.getJdbcWrapper();
        DynamicVO itemVO = (DynamicVO) event.getVo();

        try {
            JapeWrapper cabDAO = JapeFactory.dao("CabecalhoNota");
            DynamicVO cabVO = cabDAO.findByPK(new Object[]{ itemVO.asBigDecimal("NUNOTA") });

            JapeWrapper topDAO = JapeFactory.dao("TipoOperacao");
            DynamicVO topVO = topDAO.findByPK(new Object[]{ cabVO.asBigDecimal("CODTIPOPER"), cabVO.asTimestamp("DHTIPOPER") });

            if (cabVO.asString("TIPMOV").equals("P") && topVO.asBoolean("AD_TRATACORTE")) {
                System.out.println("[gwcode] item: " + itemVO.asBigDecimal("CODPROD") + " antes de rodar STP_RETRESERVA_MTF");

                CallableStatement cstm = null;
                BigDecimal qtdNaoAtendida;
                BigDecimal qtdAtendida;

                try {
                    jdbc.openSession();
                    Connection conn = jdbc.getConnection();

                    cstm = conn.prepareCall("{call STP_RETRESERVA_MTF(:P_CODPROD,:P_NUNOTA,:P_SEQUENCIA,:P_QTDATEND,:P_QTDNAOATEND)}");
                    cstm.setBigDecimal("P_CODPROD", itemVO.asBigDecimal("CODPROD"));
                    cstm.setBigDecimal("P_NUNOTA", itemVO.asBigDecimal("AD_NUNOTAPED"));
                    cstm.setBigDecimal("P_SEQUENCIA", itemVO.asBigDecimal("AD_SEQUENCIAPED"));

                    cstm.registerOutParameter("P_QTDATEND", java.sql.Types.NUMERIC);
                    cstm.registerOutParameter("P_QTDNAOATEND", java.sql.Types.NUMERIC);

                    cstm.execute();

                    qtdNaoAtendida = cstm.getBigDecimal("P_QTDNAOATEND");
                    qtdAtendida = cstm.getBigDecimal("P_QTDATEND");
                } finally {
                    JdbcUtils.closeStatement(cstm);
                    jdbc.closeSession();
                }

                System.out.println("[gwcode] item: " + itemVO.asBigDecimal("CODPROD") +
                        " qtdNaoAtend: " + qtdNaoAtendida +
                        " qtdAtend: " + qtdAtendida);

                if (!cabVO.asBoolean("AD_ACEITAFATPAR") && qtdNaoAtendida.compareTo(BigDecimal.ZERO) > 0) {
                    PersistentLocalEntity ple = dwf.findEntityByPrimaryKey("CabecalhoNota", cabVO.asBigDecimal("NUNOTA"));
                    ple.remove();
                    throw new Exception("Operação não permitida! Pedido não aceita faturamento parcial.");
                }

                if (topVO.asBoolean("AD_TRATAAGRUPTRANSFMIX") && qtdAtendida.compareTo(itemVO.asBigDecimal("QTDNEG")) != 0) {
                    JapeWrapper mixDAO = JapeFactory.dao("MIXPRO");
                    DynamicVO mixVO = mixDAO.findByPK(new Object[]{ cabVO.asBigDecimal("CODPARC"), itemVO.asBigDecimal("CODPROD") });

                    if (mixVO != null) {
                        BigDecimal agrupMix = mixVO.asBigDecimalOrZero("AGRUPTRANSFMIX");

                        if (agrupMix.compareTo(BigDecimal.ONE) > 0) {
                            BigDecimal vezes = qtdAtendida.divide(agrupMix, 0, RoundingMode.DOWN);
                            qtdAtendida = vezes.multiply(agrupMix);
                        }
                    }
                }

                itemVO.setProperty("QTDNEG", qtdAtendida.setScale(2, RoundingMode.HALF_UP));

                if (qtdAtendida.compareTo(BigDecimal.ZERO) == 0) {
                    itemVO.setProperty("PENDENTE", "N");
                }

                JapeWrapper pedidoDAO = JapeFactory.dao("ItemNota");
                DynamicVO itemPedidoVO = pedidoDAO.findByPK(new Object[]{ itemVO.asBigDecimal("AD_NUNOTAPED"), itemVO.asBigDecimal("AD_SEQUENCIAPED") });

                BigDecimal vlrUnit = itemVO.asBigDecimal("VLRUNIT");
                BigDecimal qtdNeg = itemVO.asBigDecimal("QTDNEG");
                BigDecimal percDesc = itemPedidoVO.asBigDecimal("PERCDESC");

                BigDecimal vlrTot = vlrUnit.multiply(qtdNeg).setScale(2, RoundingMode.HALF_UP);
                BigDecimal vlrDesc = vlrTot.multiply(percDesc).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                itemVO.setProperty("VLRDESC", vlrDesc);
                itemVO.setProperty("VLRTOT", vlrTot);

                System.out.println("[gwcode] item: " + itemVO.asBigDecimal("CODPROD") +
                        " finalizou corte QTDNEG: " + qtdNeg +
                        " VLRDESC: " + vlrDesc +
                        " VLRTOT: " + vlrTot);
            }

        } catch (Exception e) {
            StackTraceElement[] trace = e.getStackTrace();
            StringBuilder msg = new StringBuilder();

            for (StackTraceElement erro : trace) {
                msg.append("\nFile:" + erro.getFileName() + " Class:" + erro.getClassName() + " Method:" + erro.getMethodName() + " Line:" + erro.getLineNumber());
                if (erro.getClassName().contains("snkcampinas")) {
                    break;
                }
            }

            String log = e.getMessage() + msg;
            System.out.println("[gwcode] item: " + itemVO.asBigDecimal("CODPROD") + " error: " + log);

            throw new Exception(log);
        }
    }
}
