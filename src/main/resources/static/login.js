const form = document.querySelector("#login-form");
const message = document.querySelector("#login-message");
const csrfInput = document.querySelector("#csrf-token");
const submitButton = document.querySelector("#login-button");

document.addEventListener("DOMContentLoaded", initializeLogin);

async function initializeLogin() {
    showQueryMessage();
    try {
        const response = await fetch("/api/csrf", { credentials: "same-origin" });
        if (!response.ok) throw new Error("安全校验初始化失败");
        const csrf = await response.json();
        csrfInput.name = csrf.parameterName;
        csrfInput.value = csrf.token;
    } catch (error) {
        showMessage("暂时无法初始化安全校验，请刷新页面后重试。");
    }

    form.addEventListener("submit", event => {
        if (!csrfInput.value) {
            event.preventDefault();
            showMessage("安全校验尚未完成，请稍后重试。");
            return;
        }
        submitButton.disabled = true;
        submitButton.textContent = "正在验证…";
    });
}

function showQueryMessage() {
    const query = new URLSearchParams(window.location.search);
    if (query.has("error")) {
        showMessage("访问码不正确，请确认后重新输入。");
        document.querySelector("#access-code").setAttribute("aria-invalid", "true");
    } else if (query.has("logout")) {
        showMessage("你已安全退出演示系统。", false);
    } else if (query.has("expired")) {
        showMessage("登录状态已过期，请重新验证访问码。");
    }
}

function showMessage(text, isError = true) {
    message.textContent = text;
    message.hidden = false;
    message.classList.toggle("is-neutral", !isError);
}
