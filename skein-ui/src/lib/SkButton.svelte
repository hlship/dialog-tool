<script>

  export let disabled = false;
  export let selected = false;

  $: composedClass = composeClass(disabled, selected);

  function composeClass(disabled, selected) {
    let s = "font-black text-white rounded-lg p-2 drop-shadow-lg flex-none mx-2 text-xs";

    if (disabled) {
        s += " bg-zinc-400";
    } else if (selected) {
        s += " bg-green-400";
    } else {
        s += " bg-blue-600 hover:bg-blue-800 hover:drop-shadow-xl";
    }

    return s;
  }

</script>

<button class={composedClass} 
    disabled={disabled || selected}
on:click><slot/></button>
