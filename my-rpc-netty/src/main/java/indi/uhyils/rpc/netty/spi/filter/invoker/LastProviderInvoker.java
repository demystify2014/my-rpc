package indi.uhyils.rpc.netty.spi.filter.invoker;

import indi.uhyils.rpc.enums.RpcTypeEnum;
import indi.uhyils.rpc.exception.RpcException;
import indi.uhyils.rpc.exchange.pojo.data.NormalRpcResponseFactory;
import indi.uhyils.rpc.exchange.pojo.data.RpcData;
import indi.uhyils.rpc.exchange.pojo.data.RpcFactoryProducer;
import indi.uhyils.rpc.netty.callback.RpcCallBack;
import indi.uhyils.rpc.netty.pojo.InvokeResult;
import indi.uhyils.rpc.netty.spi.filter.FilterContext;
import indi.uhyils.rpc.netty.spi.step.RpcStep;
import indi.uhyils.rpc.netty.spi.step.template.ProviderRequestDataExtension;
import indi.uhyils.rpc.netty.spi.step.template.ProviderResponseByteExtension;
import indi.uhyils.rpc.netty.spi.step.template.ProviderResponseDataExtension;
import indi.uhyils.rpc.spi.RpcSpiManager;
import indi.uhyils.rpc.util.LogUtil;
import io.netty.buffer.ByteBuf;
import java.util.List;

/**
 * @author uhyils <247452312@qq.com>
 * @date 文件创建日期 2021年01月19日 07时27分
 */
public class LastProviderInvoker implements RpcInvoker {

    /**
     * 回调
     */
    private final RpcCallBack callback;


    /**
     * 生产者接收请求data拦截器
     */
    private List<ProviderRequestDataExtension> providerRequestDataFilters;

    /**
     * 生产者接收请求处理完成后的data拦截器
     */
    private List<ProviderResponseDataExtension> providerResponseDataFilters;

    /**
     * 生产者接收请求处理完成后byte拦截器
     */
    private List<ProviderResponseByteExtension> providerResponseByteFilters;


    public LastProviderInvoker(RpcCallBack callback) {
        this.callback = callback;

        providerRequestDataFilters = RpcSpiManager.getExtensionsByClass(RpcStep.class, ProviderRequestDataExtension.class);
        providerResponseDataFilters = RpcSpiManager.getExtensionsByClass(RpcStep.class, ProviderResponseDataExtension.class);
        providerResponseByteFilters = RpcSpiManager.getExtensionsByClass(RpcStep.class, ProviderResponseByteExtension.class);
    }

    @Override
    public RpcData invoke(FilterContext context) throws RpcException, ClassNotFoundException {

        RpcData rpcData = null;
        try {

            rpcData = context.getRequestData();
            // ProviderRequestDataFilter
            for (ProviderRequestDataExtension filter : providerRequestDataFilters) {
                rpcData = filter.doFilter(rpcData);
            }
            RpcData assembly = doInvoke(rpcData);
            // ProviderResponseDataFilter
            for (ProviderResponseDataExtension filter : providerResponseDataFilters) {
                assembly = filter.doFilter(assembly);
            }
            byte[] result = assembly.toBytes();
            for (ProviderResponseByteExtension providerResponseByteFilter : providerResponseByteFilters) {
                result = providerResponseByteFilter.doFilter(result);
            }
            return RpcFactoryProducer.build(RpcTypeEnum.RESPONSE).createByBytes(result);
        } catch (Throwable e) {
            LogUtil.error(this, e);
            if (rpcData != null) {
                return new NormalRpcResponseFactory().createErrorResponse(rpcData.unique(), e, null);
            }
        }
        throw new RpcException("netty provider wrong !!");
    }

    /**
     * 执行业务
     *
     * @param rpcData
     *
     * @return
     *
     * @throws RpcException
     * @throws ClassNotFoundException
     */
    private RpcData doInvoke(RpcData rpcData) throws RpcException, ClassNotFoundException {
        InvokeResult invoke = callback.invoke(rpcData.content());
        return callback.assembly(rpcData.unique(), invoke);
    }

    private byte[] receiveByte(ByteBuf msg) {
        /*接收并释放byteBuf*/
        byte[] bytes = new byte[msg.readableBytes()];
        msg.readBytes(bytes);
        return bytes;
    }

}
