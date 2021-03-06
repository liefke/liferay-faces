/**
 * Copyright (c) 2000-2014 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package com.liferay.faces.alloy.component.inputfile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.faces.application.Application;
import javax.faces.application.ProjectStage;
import javax.faces.application.ResourceDependencies;
import javax.faces.application.ResourceDependency;
import javax.faces.application.ViewHandler;
import javax.faces.component.NamingContainer;
import javax.faces.component.UIComponent;
import javax.faces.component.UIForm;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.convert.ConverterException;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.SystemEvent;
import javax.faces.event.SystemEventListener;
import javax.faces.render.FacesRenderer;
import javax.faces.validator.Validator;

import com.liferay.faces.alloy.component.inputfile.internal.AjaxParameters;
import com.liferay.faces.alloy.component.inputfile.internal.InputFileDecoder;
import com.liferay.faces.alloy.component.inputfile.internal.InputFileDelegationResponseWriter;
import com.liferay.faces.alloy.component.inputfile.internal.PreviewTableTemplate;
import com.liferay.faces.alloy.component.inputfile.internal.ProgressTableTemplate;
import com.liferay.faces.util.component.ComponentUtil;
import com.liferay.faces.util.component.Styleable;
import com.liferay.faces.util.config.ApplicationConfig;
import com.liferay.faces.util.context.MessageContext;
import com.liferay.faces.util.context.MessageContextFactory;
import com.liferay.faces.util.context.map.MultiPartFormData;
import com.liferay.faces.util.factory.FactoryExtensionFinder;
import com.liferay.faces.util.js.JavaScriptArray;
import com.liferay.faces.util.js.JavaScriptFragment;
import com.liferay.faces.util.lang.StringPool;
import com.liferay.faces.util.logging.Logger;
import com.liferay.faces.util.logging.LoggerFactory;
import com.liferay.faces.util.model.UploadedFile;
import com.liferay.faces.util.product.ProductConstants;
import com.liferay.faces.util.product.ProductMap;
import com.liferay.faces.util.render.DelegationResponseWriter;
import com.liferay.faces.util.render.RendererUtil;


/**
 * @author  Neil Griffin
 */
//J-
@FacesRenderer(componentFamily = InputFile.COMPONENT_FAMILY, rendererType = InputFile.RENDERER_TYPE)
@ResourceDependencies(
		{
			@ResourceDependency(library = "liferay-faces-alloy", name = "alloy.css"),
			@ResourceDependency(library = "liferay-faces-alloy", name = "alloy.js"),
			@ResourceDependency(library = "liferay-faces-reslib", name = "build/aui-css/css/bootstrap.min.css"),
			@ResourceDependency(library = "liferay-faces-reslib", name = "build/aui/aui-min.js"),
			@ResourceDependency(library = "liferay-faces-reslib", name = "liferay.js")
		}
	)
//J+
public class InputFileRenderer extends InputFileRendererCompat implements SystemEventListener {

	// Logger
	private static final Logger logger = LoggerFactory.getLogger(InputFileRenderer.class);

	// Private Constants
	private static final boolean LIFERAY_FACES_BRIDGE_DETECTED = ProductMap.getInstance().get(
			ProductConstants.LIFERAY_FACES_BRIDGE).isDetected();

	// Protected Constants
	protected static final String[] MODULES = { "uploader", "aui-datatable", "datatype-xml" };

	@Override
	public void decode(FacesContext facesContext, UIComponent uiComponent) {

		InputFile inputFile = (InputFile) uiComponent;

		Map<String, List<UploadedFile>> uploadedFileMap = getUploadedFileMap(facesContext, inputFile.getLocation());

		if (uploadedFileMap != null) {

			String clientId = uiComponent.getClientId(facesContext);
			List<UploadedFile> uploadedFiles = uploadedFileMap.get(clientId);

			if ((uploadedFiles != null) && (uploadedFiles.size() > 0)) {

				inputFile.setSubmittedValue(uploadedFiles);

				// Queue the FileUploadEvent so that each uploaded file can be handled individually with an
				// ActionListener.
				for (UploadedFile uploadedFile : uploadedFiles) {

					FileUploadEvent fileUploadEvent = new FileUploadEvent(uiComponent, uploadedFile);
					uiComponent.queueEvent(fileUploadEvent);
				}
			}
		}
	}

	@Override
	public void encodeJavaScriptCustom(FacesContext facesContext, UIComponent uiComponent) throws IOException {

		ResponseWriter responseWriter = facesContext.getResponseWriter();
		InputFile inputFile = (InputFile) uiComponent;
		String clientId = inputFile.getClientId(facesContext);

		// Determine the valid content-types and maximum file size from the validator (if specified).
		JavaScriptArray contentTypes = new JavaScriptArray();
		JavaScriptFragment alloyNamespace = new JavaScriptFragment("A");
		long maxFileSize = Long.MAX_VALUE;
		InputFileValidator inputFileValidator = getInputFileValidator(inputFile);

		if (inputFileValidator != null) {
			String validContentTypes = inputFileValidator.getContentTypes();

			if (validContentTypes != null) {
				contentTypes = new JavaScriptArray(validContentTypes.split(","));
			}

			maxFileSize = inputFileValidator.getMaxFileSize();
		}

		// If the component should render the upload progress table, then initialize the YUI progress uploader widget.
		if (inputFile.isShowProgress()) {

			String clientVarName = ComponentUtil.getClientVarName(facesContext, inputFile);
			String clientKey = inputFile.getClientKey();

			if (clientKey == null) {
				clientKey = clientVarName;
			}

			UIViewRoot viewRoot = facesContext.getViewRoot();
			Locale locale = viewRoot.getLocale();
			String formClientId = getParentFormClientId(inputFile);
			Application application = facesContext.getApplication();
			ViewHandler viewHandler = application.getViewHandler();
			String actionURL = viewHandler.getActionURL(facesContext, viewRoot.getViewId());
			String partialActionURL = facesContext.getExternalContext().encodePartialActionURL(actionURL);
			String namingContainerId = StringPool.BLANK;

			if (viewRoot instanceof NamingContainer) {
				namingContainerId = viewRoot.getContainerClientId(facesContext);
			}

			AjaxParameters ajaxParameters = new AjaxParameters(inputFile, clientId, formClientId);
			String execute = ajaxParameters.getExecute();
			String render = ajaxParameters.getRender();

			String notStartedMessage = getMessageContext().getMessage(locale, "not-started");
			JavaScriptFragment clientComponent = new JavaScriptFragment("Liferay.component('" + clientKey + "')");
			RendererUtil.encodeFunctionCall(responseWriter, "LFAI.initProgressUploader", alloyNamespace,
				clientComponent, contentTypes, clientId, formClientId, namingContainerId, inputFile.isAuto(), execute,
				render, partialActionURL, maxFileSize, notStartedMessage);
		}

		// Otherwise, if the component should render the upload preview table, then format the preview-uploader.js
		// template and write it to the response.
		else if (inputFile.isShowPreview()) {

			RendererUtil.encodeFunctionCall(responseWriter, "LFAI.initPreviewUploader", alloyNamespace, contentTypes,
				clientId, maxFileSize);
		}
	}

	@Override
	public void encodeMarkupBegin(FacesContext facesContext, UIComponent uiComponent) throws IOException {
		ResponseWriter responseWriter = facesContext.getResponseWriter();

		InputFile inputFile = (InputFile) uiComponent;

		// If the component should render the preview table or the upload progress table, then
		if (inputFile.isShowPreview() || inputFile.isShowProgress()) {

			// Start encoding the outermost <div> element.
			responseWriter.startElement(StringPool.DIV, uiComponent);
			responseWriter.writeAttribute(StringPool.ID, uiComponent.getClientId(facesContext), StringPool.ID);
			RendererUtil.encodeStyleable(responseWriter, (Styleable) uiComponent);

			// If the component should render the upload progress table, then format the progress-table.html template
			// and write it to the response.
			String clientId = uiComponent.getClientId(facesContext);
			Locale locale = facesContext.getViewRoot().getLocale();

			if (inputFile.isShowProgress()) {
				ProgressTableTemplate progressTableTemplate = getProgressTableTemplate(facesContext);
				String progressTableHTML = progressTableTemplate.format(locale, clientId, inputFile.isAuto());
				responseWriter.write(progressTableHTML);
			}

			// Otherwise, delegate writing to the delegate renderer. Note that this effectively a no-op with Mojarra and
			// MyFaces, since they both delay writing of the entire <input type="file"...> ... </input> element until
			// encodeEnd.
			else {
				super.encodeMarkupBegin(facesContext, uiComponent);
			}
		}

		// Otherwise, delegate writing to the delegate renderer. Note that this effectively a no-op with Mojarra and
		// MyFaces, since they both delay writing of the entire <input type="file"...> ... </input> element until
		// encodeEnd.
		else {
			super.encodeMarkupBegin(facesContext, uiComponent);
		}
	}

	@Override
	public void encodeMarkupEnd(FacesContext facesContext, UIComponent uiComponent) throws IOException {

		// If the component should show the progress table, then
		InputFile inputFile = (InputFile) uiComponent;
		ResponseWriter responseWriter = facesContext.getResponseWriter();

		if (inputFile.isShowProgress()) {

			// Finish encoding of the outermost <div> element. Since the template contains its own "Select Files"
			// button, delegation must not occur.
			responseWriter.endElement(StringPool.DIV);
		}

		// Otherwise, if the component should show the preview table, then
		else if (inputFile.isShowPreview()) {

			// Delegate writing of the entire <input type="file"...> ... </input> element to the delegate renderer.
			DelegationResponseWriter delegationResponseWriter = new InputFileDelegationResponseWriter(responseWriter,
					inputFile.isAuto());
			super.encodeMarkupEnd(facesContext, uiComponent, delegationResponseWriter);

			// Format the preview-table.html template and write it to the response.
			Locale locale = facesContext.getViewRoot().getLocale();
			String clientId = uiComponent.getClientId(facesContext);
			PreviewTableTemplate previewTableTemplate = getPreviewTableTemplate(facesContext);
			String previewTableHTML = previewTableTemplate.format(locale, clientId, false);
			responseWriter.write(previewTableHTML);

			// Finish encoding of the outermost <div> element.
			responseWriter.endElement(StringPool.DIV);
		}

		// Otherwise, delegate writing of the entire <input type="file"...> ... </input> element to the delegate
		// renderer.
		else {
			DelegationResponseWriter delegationResponseWriter = new InputFileDelegationResponseWriter(responseWriter,
					inputFile.isAuto());
			super.encodeMarkupEnd(facesContext, uiComponent, delegationResponseWriter);
		}
	}

	@Override
	public void processEvent(SystemEvent postConstructApplicationConfigEvent) throws AbortProcessingException {

		// Due to ClassLoader problems during static initialization, it is necessary to delay creation of singleton
		// instances of template classes until the PostConstructApplicationConfigEvent is sent. Although template text
		// is the same for each webapp context, the template must be stored as a ServletContext (application scoped)
		// attribute in order to avoid a race condition when contexts are started up in parallel. For example:
		// http://wiki.apache.org/tomcat/HowTo/FasterStartUp#Starting_several_web_applications_in_parallel
		try {
			FacesContext startupFacesContext = FacesContext.getCurrentInstance();
			ExternalContext externalContext = startupFacesContext.getExternalContext();
			Map<String, Object> applicationMap = externalContext.getApplicationMap();
			boolean minified = startupFacesContext.isProjectStage(ProjectStage.Production);
			applicationMap.put(PreviewTableTemplate.class.getName(), new PreviewTableTemplate(minified));
			applicationMap.put(ProgressTableTemplate.class.getName(), new ProgressTableTemplate(minified));
		}
		catch (Exception e) {
			logger.error(e);
		}
	}

	@Override
	protected void encodeHiddenAttributes(FacesContext facesContext, ResponseWriter responseWriter, InputFile inputFile,
		boolean first) throws IOException {

		// fileFieldName
		encodeString(responseWriter, "fileFieldName", inputFile.getClientId(), first);
		first = false;

		// multipleFiles
		String multiple = inputFile.getMultiple();
		boolean multipleFiles = "multiple".equalsIgnoreCase(multiple);
		encodeBoolean(responseWriter, "multipleFiles", multipleFiles, first);

		// selectFilesButton
		Locale locale = facesContext.getViewRoot().getLocale();
		String chooseFiles = getMessageContext().getMessage(locale, "choose-files");
		StringBuilder selectFilesButtonScript = new StringBuilder();
		selectFilesButtonScript.append(
			"A.Node.create(\"<button type='button' class='yui3-widget btn btn-content' role='button' aria-label='");
		selectFilesButtonScript.append(chooseFiles);
		selectFilesButtonScript.append("' tabindex='{tabIndex}'>");
		selectFilesButtonScript.append(chooseFiles);
		selectFilesButtonScript.append("</button>\")");
		encodeNonEscapedObject(responseWriter, "selectFilesButton", selectFilesButtonScript, first);
	}

	@Override
	public Object getConvertedValue(FacesContext facesContext, UIComponent uiComponent, Object submittedValue)
		throws ConverterException {
		return submittedValue;
	}

	@Override
	public String getDelegateComponentFamily() {
		return InputFile.DELEGATE_COMPONENT_FAMILY;
	}

	@Override
	public String getDelegateRendererType() {
		return InputFile.DELEGATE_RENDERER_TYPE;
	}

	@Override
	public boolean isListenerForSource(Object source) {
		return ((source != null) && (source instanceof ApplicationConfig));
	}

	protected InputFileValidator getInputFileValidator(InputFile inputFile) {

		InputFileValidator inputFileValidator = null;
		Validator[] validators = inputFile.getValidators();

		for (Validator validator : validators) {

			if (validator instanceof InputFileValidator) {
				inputFileValidator = (InputFileValidator) validator;

				break;
			}
		}

		return inputFileValidator;
	}

	protected MessageContext getMessageContext() {

		MessageContextFactory messageContextFactory = (MessageContextFactory) FactoryExtensionFinder.getFactory(
				MessageContextFactory.class);

		return messageContextFactory.getMessageContext();
	}

	@Override
	protected String[] getModules(FacesContext facesContext, UIComponent uiComponent) {
		return MODULES;
	}

	protected String getParentFormClientId(UIComponent uiComponent) {

		String parentFormClientId = null;

		if (uiComponent != null) {

			if (uiComponent instanceof UIForm) {
				parentFormClientId = uiComponent.getClientId();
			}
			else {
				parentFormClientId = getParentFormClientId(uiComponent.getParent());
			}
		}

		return parentFormClientId;
	}

	protected PreviewTableTemplate getPreviewTableTemplate(FacesContext facesContext) {
		ExternalContext externalContext = facesContext.getExternalContext();
		Map<String, Object> applicationMap = externalContext.getApplicationMap();

		return (PreviewTableTemplate) applicationMap.get(PreviewTableTemplate.class.getName());
	}

	protected ProgressTableTemplate getProgressTableTemplate(FacesContext facesContext) {
		ExternalContext externalContext = facesContext.getExternalContext();
		Map<String, Object> applicationMap = externalContext.getApplicationMap();

		return (ProgressTableTemplate) applicationMap.get(ProgressTableTemplate.class.getName());
	}

	protected Map<String, List<UploadedFile>> getUploadedFileMap(FacesContext facesContext, String location) {

		Map<String, List<UploadedFile>> uploadedFileMap;

		if (LIFERAY_FACES_BRIDGE_DETECTED) {
			Map<String, Object> requestAttributeMap = facesContext.getExternalContext().getRequestMap();
			MultiPartFormData multiPartFormData = (MultiPartFormData) requestAttributeMap.get(MultiPartFormData.class
					.getName());
			uploadedFileMap = multiPartFormData.getUploadedFileMap();
		}
		else {
			InputFileDecoder inputFileDecoder = getInputFileDecoder();
			uploadedFileMap = inputFileDecoder.decode(facesContext, location);
		}

		return uploadedFileMap;
	}
}
