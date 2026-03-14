/**
 * app.js — Scripts globales del Sistema de Eventos Académicos - PUCMM
 * Punto 10: Validaciones con JavaScript
 */

document.addEventListener('DOMContentLoaded', function ()
{
    activarValidacionBootstrap();

    const formEvento = document.getElementById('form-evento');
    if (formEvento) validarFormularioEvento(formEvento);

    autoCerrarAlertas();
});

function activarValidacionBootstrap()
{
    document.querySelectorAll('form.needs-validation').forEach(form => {
        form.addEventListener('submit', function (e) {
            if (!form.checkValidity()) {
                e.preventDefault();
                e.stopPropagation();
            }
            form.classList.add('was-validated');
        });
    });
}

function validarFormularioEvento(form)
{
    const campoFecha  = form.querySelector('[name="fechaHora"]');
    const campoCupo   = form.querySelector('[name="cupoMaximo"]');
    const campoTitulo = form.querySelector('[name="titulo"]');

    form.addEventListener('submit', function (e)
    {
        let valido = true;

        if (campoTitulo && campoTitulo.value.trim().length < 3)
        {
            campoTitulo.setCustomValidity('El título debe tener al menos 3 caracteres');
            valido = false;
        } else if (campoTitulo)
        {
            campoTitulo.setCustomValidity('');
        }

        if (campoFecha && campoFecha.value)
        {
            if (new Date(campoFecha.value) <= new Date())
            {
                campoFecha.setCustomValidity('La fecha y hora deben ser futuras');
                valido = false;
            } else
            {
                campoFecha.setCustomValidity('');
            }
        }

        if (campoCupo && parseInt(campoCupo.value) < 1)
        {
            campoCupo.setCustomValidity('El cupo mínimo es 1');
            valido = false;
        } else if (campoCupo)
        {
            campoCupo.setCustomValidity('');
        }

        if (!valido || !form.checkValidity())
        {
            e.preventDefault();
            e.stopPropagation();
        }
        form.classList.add('was-validated');
    });

    [campoTitulo, campoFecha, campoCupo].forEach(campo =>
    {
        if (campo) campo.addEventListener('input', () => campo.setCustomValidity(''));
    });
}

function autoCerrarAlertas()
{
    document.querySelectorAll('.alert-success.alert-dismissible').forEach(alerta =>
    {
        setTimeout(() =>
        {
            const bsAlert = bootstrap.Alert.getOrCreateInstance(alerta);
            if (bsAlert) bsAlert.close();
        }, 4000);
    });
}

async function apiFetch(url, method = 'GET', body = null)
{
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body) opts.body = JSON.stringify(body);
    const resp = await fetch(url, opts);
    const data = await resp.json();
    if (!resp.ok) throw new Error(data.error || `Error ${resp.status}`);
    return data;
}