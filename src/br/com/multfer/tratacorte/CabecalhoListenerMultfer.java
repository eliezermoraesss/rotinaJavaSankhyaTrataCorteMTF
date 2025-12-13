package br.com.multfer.tratacorte;

import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.util.FinderWrapper;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

import java.math.BigDecimal;

public class CabecalhoListenerMultfer implements EventoProgramavelJava {

    public void beforeUpdate(PersistenceEvent event) throws Exception {}

    public void afterDelete(PersistenceEvent event) throws Exception {}

    public void afterInsert(PersistenceEvent event) throws Exception {}

    public void beforeCommit(TransactionContext ctx) throws Exception {}

    public void beforeDelete(PersistenceEvent event) throws Exception {}

    public void beforeInsert(PersistenceEvent event) throws Exception {}

    public void afterUpdate(PersistenceEvent event) throws Exception {
        DynamicVO notaVO = (DynamicVO) event.getVo();

        // Remoção com DWFFacade (correto)
        deleteItensZerados(notaVO);

        // Zerar BASEICMS dos itens quando alterar frete
        if ((event.getModifingFields().isModifing("TIPFRETE")
                || event.getModifingFields().isModifing("VLRFRETE"))
                && "O".equals(notaVO.asString("TIPMOV"))) {

            try {
                EntityFacade dwf = EntityFacadeFactory.getDWFFacade();
                FinderWrapper finder = new FinderWrapper(
                        "ItemNota",
                        "this.NUNOTA = ?",
                        new Object[]{notaVO.asBigDecimal("NUNOTA")}
                );

                for (Object ple : dwf.findByDynamicFinder(finder)) {
                    EntityVO itemVO = ((br.com.sankhya.jape.bmp.PersistentLocalEntity) ple).getValueObject();
                    DynamicVO dyn = (DynamicVO) itemVO;

                    System.out.println("[gwcode] afterUpdate item: " + dyn.asBigDecimal("CODPROD") + " zerando BASEICMS");
                    dyn.setProperty("BASEICMS", BigDecimal.ZERO);

                    ((br.com.sankhya.jape.bmp.PersistentLocalEntity) ple).setValueObject((EntityVO) dyn);

                    System.out.println("[gwcode] afterUpdate item: " + dyn.asBigDecimal("CODPROD") + " concluído BASEICMS");
                }

            } catch (Exception e) {
                throw formatException(e);
            }
        }
    }

    // -------------------------------------------------------------
    // 🔥 NOVA VERSÃO SEM PersistentLocalEntity
    // -------------------------------------------------------------
    public void deleteItensZerados(DynamicVO notaVO) throws Exception {
        if ("P".equals(notaVO.asString("TIPMOV"))) {
            try {
                EntityFacade dwf = EntityFacadeFactory.getDWFFacade();

                System.out.println("[gwcode] Removendo itens zerados da nota: " + notaVO.asBigDecimal("NUNOTA"));

                dwf.removeByCriteria(
                        new FinderWrapper(
                                "ItemNota",
                                "this.NUNOTA = ? AND this.QTDNEG = 0",
                                new Object[]{notaVO.asBigDecimal("NUNOTA")}
                        )
                );

                System.out.println("[gwcode] Itens de quantidade zero removidos com sucesso!");

            } catch (Exception e) {
                throw formatException(e);
            }
        }
    }

    // -------------------------------------------------------------
    // 🧰 Padronização de erros
    // -------------------------------------------------------------
    private Exception formatException(Exception e) {
        StackTraceElement[] trace = e.getStackTrace();
        StringBuilder msg = new StringBuilder();

        for (StackTraceElement erro : trace) {
            msg.append("\nFile:" + erro.getFileName()
                    + " Class:" + erro.getClassName()
                    + " Method:" + erro.getMethodName()
                    + " Line:" + erro.getLineNumber());

            if (erro.getClassName().contains("snkcampinas"))
                break;
        }

        return new Exception(e.getMessage() + msg);
    }

}
