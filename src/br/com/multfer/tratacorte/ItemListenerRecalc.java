package br.com.multfer.tratacorte;

import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper;

public class ItemListenerRecalc implements EventoProgramavelJava {
    public void beforeUpdate(PersistenceEvent event) throws Exception {
    }

    public void afterDelete(PersistenceEvent arg0) throws Exception {
    }

    public void afterInsert(PersistenceEvent event) throws Exception {
        DynamicVO itemVO = (DynamicVO)event.getVo();
        if (itemVO.asString("USOPROD").equals("R")) {
            JapeWrapper cabDAO = JapeFactory.dao("CabecalhoNota");
            DynamicVO cabVO = cabDAO.findByPK(new Object[]{itemVO.asBigDecimal("NUNOTA")});
            DynamicVO topVO = cabVO.asDymamicVO("TipoOperacao");
            if (topVO.asBoolean("AD_VALCAMPDESC")) {
                ImpostosHelpper impHelpper = new ImpostosHelpper();
                impHelpper.setForcarRecalculo(true);
                impHelpper.calcularImpostos(itemVO.asBigDecimal("NUNOTA"));
            }
        }

    }

    public void afterUpdate(PersistenceEvent event) throws Exception {
        try {
            DynamicVO itemVO = (DynamicVO)event.getVo();
            if (itemVO.asString("USOPROD").equals("R") && event.getModifingFields().isModifing("AD_CODCAMP")) {
                ImpostosHelpper impHelpper = new ImpostosHelpper();
                impHelpper.setForcarRecalculo(true);
                impHelpper.calcularImpostos(itemVO.asBigDecimal("NUNOTA"));
            }
        } catch (Exception var4) {
        }

    }

    public void beforeCommit(TransactionContext arg0) throws Exception {
    }

    public void beforeDelete(PersistenceEvent arg0) throws Exception {
    }

    public void beforeInsert(PersistenceEvent event) throws Exception {
    }
}
